export type SessionUser = {
  id: number;
  username: string;
  email: string;
  displayName: string;
  status: string;
  roles: string[];
  permissions: string[];
};

type CsrfResponse = {
  headerName: string;
  token: string;
};

export async function getCsrfToken(): Promise<CsrfResponse> {
  const response = await fetch("/api/v1/auth/csrf", {
    credentials: "same-origin",
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error("Unable to prepare a secure request.");
  return (await response.json()) as CsrfResponse;
}

export async function getSessionUser(): Promise<SessionUser | null> {
  const response = await fetch("/api/v1/auth/me", {
    credentials: "same-origin",
    headers: { Accept: "application/json" },
  });
  if (response.status === 401) return null;
  if (!response.ok) throw new Error("Unable to read the current account.");
  return (await response.json()) as SessionUser;
}

export async function sendJson<T>(path: string, method: "POST" | "PUT", body: unknown): Promise<T> {
  const csrf = await getCsrfToken();
  const response = await fetch(path, {
    method,
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    let detail = "The request could not be completed.";
    try {
      const problem = (await response.json()) as { detail?: string };
      detail = problem.detail ?? detail;
    } catch {
      // Keep a generic message when the API does not return a problem document.
    }
    throw new Error(detail);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}
