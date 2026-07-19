import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

const protectedPaths = ['/dashboard', '/conditions', '/reports'];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // 루트 경로는 대시보드로 리다이렉트
  if (pathname === '/') {
    return NextResponse.redirect(new URL('/dashboard', request.url));
  }

  const isProtected = protectedPaths.some((p) => pathname.startsWith(p));

  if (!isProtected) return NextResponse.next();

  // 미들웨어는 서버사이드라 accessToken(메모리)을 볼 수 없으므로,
  // refresh_token 쿠키 존재 여부로 인증 상태 판별
  const hasRefreshToken = request.cookies.has('refreshToken');

  if (!hasRefreshToken) {
    return NextResponse.redirect(new URL('/login', request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/', '/dashboard/:path*', '/conditions/:path*', '/reports/:path*'],
};
