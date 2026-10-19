const DEFAULT_GATEWAY_URL = "http://localhost:8080";

export const CORE_GATEWAY_URL =
  process.env.NEXT_PUBLIC_CORE_GATEWAY_URL?.replace(/\/$/, "") || DEFAULT_GATEWAY_URL;

export const AUTH_LOGIN_URL = `${CORE_GATEWAY_URL}/api/auth/login`;
export const AUTH_BIOMETRIC_CHALLENGE_URL = `${CORE_GATEWAY_URL}/api/auth/biometric-challenge`;
export const AUTH_BIOMETRIC_CONFIRM_URL = `${CORE_GATEWAY_URL}/api/auth/biometric-confirm`;
export const VOTE_CAST_URL = `${CORE_GATEWAY_URL}/api/vote/cast`;
export const ELECTIONS_ACTIVE_URL = `${CORE_GATEWAY_URL}/api/elections/active`;
export const ADMIN_ELECTIONS_URL = `${CORE_GATEWAY_URL}/api/admin/elections`;
