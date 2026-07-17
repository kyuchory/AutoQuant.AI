export interface UserInfo {
  userId: number;
  nickname: string;
  email: string;
}

export interface LoginResponse {
  accessToken: string;
  accessTokenExpiresIn: number;
  isNewUser: boolean;
  user: UserInfo;
}

export interface RefreshResponse {
  accessToken: string;
  accessTokenExpiresIn: number;
  user: UserInfo;
}
