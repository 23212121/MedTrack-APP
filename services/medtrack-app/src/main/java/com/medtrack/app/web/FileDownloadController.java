package com.medtrack.app.web;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Downloads a direct HTTP(S) file URL and streams it to the browser as an attachment.
 * Blocks private networks and known streaming sites (e.g. YouTube).
 */
@RestController
@RequestMapping("/api/download")
public class FileDownloadController {
  private static final int CONNECT_TIMEOUT_MS = 15_000;
  private static final int READ_TIMEOUT_MS = 60_000;
  private static final long MAX_BYTES = 100L * 1024 * 1024; // 100 MB

  private static final Set<String> BLOCKED_HOST_SUFFIXES =
      Set.of(
          "youtube.com",
          "youtu.be",
          "youtube-nocookie.com",
          "googlevideo.com",
          "vimeo.com",
          "dailymotion.com");

  @GetMapping("/file")
  public ResponseEntity<StreamingResponseBody> download(@RequestParam("url") String rawUrl) {
    URI uri = parseAndValidate(rawUrl);
    try {
      HttpURLConnection conn = open(uri.toURL());
      int code = conn.getResponseCode();
      if (code >= 400) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "Remote file returned HTTP " + code);
      }

      String contentType = conn.getContentType();
      long length = conn.getContentLengthLong();
      if (length > MAX_BYTES) {
        throw new ResponseStatusException(
            HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds 100 MB limit");
      }

      String filename = filenameFrom(uri, conn);
      InputStream in = conn.getInputStream();

      StreamingResponseBody body =
          output -> {
            try (in) {
              byte[] buf = new byte[8192];
              long total = 0;
              int n;
              while ((n = in.read(buf)) >= 0) {
                total += n;
                if (total > MAX_BYTES) {
                  throw new IllegalStateException("File exceeds 100 MB limit");
                }
                output.write(buf, 0, n);
              }
              output.flush();
            }
          };

      return ResponseEntity.ok()
          .header(
              HttpHeaders.CONTENT_DISPOSITION,
              "attachment; filename=\"" + filename.replace("\"", "") + "\"")
          .contentType(
              contentType != null && !contentType.isBlank()
                  ? MediaType.parseMediaType(contentType.split(";")[0].trim())
                  : MediaType.APPLICATION_OCTET_STREAM)
          .body(body);
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Could not download file: " + ex.getMessage());
    }
  }

  private static URI parseAndValidate(String rawUrl) {
    if (rawUrl == null || rawUrl.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url is required");
    }
    String trimmed = rawUrl.trim();
    URI uri;
    try {
      uri = URI.create(trimmed);
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only http/https URLs are allowed");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL host is missing");
    }
    String hostLower = host.toLowerCase(Locale.ROOT);
    for (String blocked : BLOCKED_HOST_SUFFIXES) {
      if (hostLower.equals(blocked) || hostLower.endsWith("." + blocked)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Downloading from this site is not supported");
      }
    }
    try {
      InetAddress addr = InetAddress.getByName(host);
      if (addr.isAnyLocalAddress()
          || addr.isLoopbackAddress()
          || addr.isLinkLocalAddress()
          || addr.isSiteLocalAddress()
          || addr.isMulticastAddress()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Local/private network URLs are not allowed");
      }
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not resolve host");
    }
    return uri;
  }

  private static HttpURLConnection open(URL url) throws Exception {
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setInstanceFollowRedirects(true);
    conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
    conn.setReadTimeout(READ_TIMEOUT_MS);
    conn.setRequestProperty("User-Agent", "MedTrack-FileDownload/1.0");
    conn.setRequestMethod("GET");
    return conn;
  }

  private static String filenameFrom(URI uri, HttpURLConnection conn) {
    String cd = conn.getHeaderField("Content-Disposition");
    if (cd != null) {
      int idx = cd.toLowerCase(Locale.ROOT).indexOf("filename=");
      if (idx >= 0) {
        String part = cd.substring(idx + 9).trim().replace("\"", "");
        int semi = part.indexOf(';');
        if (semi >= 0) part = part.substring(0, semi).trim();
        if (!part.isBlank()) {
          return sanitizeFilename(URLDecoder.decode(part, StandardCharsets.UTF_8));
        }
      }
    }
    String path = uri.getPath();
    if (path != null && path.contains("/")) {
      String last = path.substring(path.lastIndexOf('/') + 1);
      if (!last.isBlank()) {
        return sanitizeFilename(URLDecoder.decode(last, StandardCharsets.UTF_8));
      }
    }
    return "download.bin";
  }

  private static String sanitizeFilename(String name) {
    String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    return cleaned.isBlank() ? "download.bin" : cleaned;
  }
}
