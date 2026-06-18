const requestJson = async (path, payload) => {
  const response = await fetch(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  let body = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }

  if (!response.ok || body?.code === 0) {
    throw new Error(body?.message || 'Request failed. Please try again.');
  }

  return body?.data ?? null;
};

export const login = (payload) => requestJson('/api/users/login', payload);

export const register = (payload) => requestJson('/api/users/register', payload);

export const resetPassword = (payload) => requestJson('/api/users/forgot-password', payload);
