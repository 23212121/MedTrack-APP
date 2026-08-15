import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        // MedTrack gateway (not Apache on :8080)
        target: "http://localhost:8090",
        changeOrigin: true,
      },
    },
  },
});
