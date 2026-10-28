const readResponse = async (response) => {
  let body = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }
  if (!response.ok || body?.code === 0) {
    throw new Error(body?.message || 'Unable to complete sign-in. Please try again.');
  }
  return body?.data ?? null;
};

export const getOAuthProviders = async (signal) => {
  const response = await fetch('/api/auth/oauth/providers', { signal });
  return readResponse(response);
};

export const exchangeOAuthCode = async (code) => {
  const response = await fetch('/api/auth/oauth/exchange', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code }),
  });
  return readResponse(response);
};
