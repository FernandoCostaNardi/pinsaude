const KC_URL    = import.meta.env.VITE_KC_URL    || 'http://localhost:8080'
const KC_REALM  = import.meta.env.VITE_KC_REALM  || 'pinsaude'
const KC_CLIENT = import.meta.env.VITE_KC_CLIENT || 'pinsaude-web'

export const KC_TOKEN_ENDPOINT =
  `${KC_URL}/realms/${KC_REALM}/protocol/openid-connect/token`

export const KC_LOGOUT_ENDPOINT =
  `${KC_URL}/realms/${KC_REALM}/protocol/openid-connect/logout`

export const KC_RESET_PASSWORD_URL =
  `${KC_URL}/realms/${KC_REALM}/login-actions/reset-credentials?client_id=${KC_CLIENT}`

export { KC_URL, KC_REALM, KC_CLIENT }

export interface TokenResponse {
  access_token:      string
  refresh_token:     string
  id_token:          string
  expires_in:        number
  refresh_expires_in: number
  token_type:        string
}

export interface JwtPayload {
  sub:            string
  name?:          string
  given_name?:    string
  family_name?:   string
  email?:         string
  cnpj_id?:       string
  realm_access?:  { roles: string[] }
  exp:            number
  iat:            number
}

export function decodeJwt(token: string): JwtPayload {
  const payload = token.split('.')[1]
  const padded  = payload.replace(/-/g, '+').replace(/_/g, '/')
  const json    = atob(padded.padEnd(padded.length + (4 - padded.length % 4) % 4, '='))
  return JSON.parse(json) as JwtPayload
}
