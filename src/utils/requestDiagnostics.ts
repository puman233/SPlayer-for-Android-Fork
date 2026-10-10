import axios from "axios";

/** Closed diagnostic vocabulary: never return values from a request or response. */
export const requestFailureCategory = (error: unknown): string => {
  try {
    if (!axios.isAxiosError(error)) return "unknown";
    switch (error.code) {
      case "ECONNABORTED":
      case "ETIMEDOUT":
        return "timeout";
      case "ERR_CANCELED":
        return "cancelled";
      case "ERR_NETWORK":
        return "network";
    }
    return typeof error.response?.status === "number" ? "http" : "unknown";
  } catch {
    // Diagnostics must not replace the original business failure, even for hostile getters.
    return "unknown";
  }
};
