import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// base "./" giúp build chạy được trên GitLab Pages / thư mục con / hosting tĩnh bất kỳ
export default defineConfig({
  plugins: [react()],
  base: "./",
  server: { port: 5173, open: true },
});
