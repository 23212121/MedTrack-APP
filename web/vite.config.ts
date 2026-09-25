import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": {
        // MedTrack gateway (not Apache on :8080)
        target: "http://localhost:8090",
        changeOrigin: true,
      },
      "/actuator": {
        target: "http://localhost:8090",
        changeOrigin: true,
      },
    },
  },
});
