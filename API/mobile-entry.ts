import "./runtimeDiagnostics";
import { requestFailureCategory } from "../src/utils/requestDiagnostics";
process.env["SP_API_PORT"] = process.env["SP_API_PORT"] || "1145";
process.env["SP_API_HOST"] = process.env["SP_API_HOST"] || "127.0.0.1";
process.env["SP_EMBEDDED"] = "1";

process.on("uncaughtException", (error) => {
  console.error("[embedded-api] uncaughtException", requestFailureCategory(error));
});

process.on("unhandledRejection", (reason) => {
  console.error("[embedded-api] unhandledRejection", requestFailureCategory(reason));
});

void import("./mobile-server");
