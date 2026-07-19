'use client';

import { useTranslation } from 'react-i18next';
import { usePathname, useRouter } from 'next/navigation';
import { useAuth } from '@/lib/hooks/useAuth';
import { useLanguageStore } from '@/lib/store/languageStore';
import Image from 'next/image';

const NAV_ITEMS = [
  { path: '/dashboard', labelKey: 'header.dashboard' },
  { path: '/conditions', labelKey: 'header.conditions' },
  { path: '/reports', labelKey: 'header.reports' },
];

export default function Header() {
  const { t, i18n } = useTranslation();
  const pathname = usePathname();
  const router = useRouter();
  const { isLoggedIn, user, logout } = useAuth();
  const { language, setLanguage } = useLanguageStore();

  const toggleLanguage = () => {
    const newLang = language === 'ko' ? 'en' : 'ko';
    setLanguage(newLang);
    i18n.changeLanguage(newLang);
  };

  return (
    <header className="header-bar">
      {/* Left: Logo (이미지 자체에 텍스트가 포함되어 있음) */}
      <div className="header-left" onClick={() => router.push('/dashboard')} style={{ cursor: 'pointer' }}>
        <div className="header-logo">
          <Image
            src="/AutoQuant.AI_LOGO.png"
            alt="AutoQuant AI"
            width={180}
            height={50}
            style={{ objectFit: 'contain' }}
            priority
          />
        </div>
      </div>

      {/* Center: Navigation */}
      <nav className="header-nav">
        {isLoggedIn &&
          NAV_ITEMS.map((item) => (
            <button
              key={item.path}
              className={`header-nav-item ${pathname.startsWith(item.path) ? 'active' : ''}`}
              onClick={() => router.push(item.path)}
            >
              {t(item.labelKey)}
            </button>
          ))}
      </nav>

      {/* Right: Language switch + User/Login */}
      <div className="header-right">
        <button className="header-lang-btn" onClick={toggleLanguage}>
          {language === 'ko' ? 'EN' : 'KO'}
        </button>

        {isLoggedIn ? (
          <div className="header-user">
            <div className="header-user-info">
              <span className="header-nickname">{user?.nickname}</span>
            </div>
            <button className="header-logout-btn" onClick={logout}>
              {t('common.logout')}
            </button>
          </div>
        ) : (
          <button className="header-login-btn" onClick={() => router.push('/login')}>
            {t('common.login')}
          </button>
        )}
      </div>

      <style jsx>{`
        .header-bar {
          display: flex;
          align-items: center;
          justify-content: space-between;
          height: 64px;
          padding: 0 24px;
          background: rgba(10, 14, 23, 0.95);
          backdrop-filter: blur(12px);
          -webkit-backdrop-filter: blur(12px);
          border-bottom: 1px solid var(--border-color);
          position: sticky;
          top: 0;
          z-index: 100;
        }

        .header-left {
          display: flex;
          align-items: center;
          gap: 12px;
        }

        .header-logo {
          display: flex;
          align-items: center;
          justify-content: center;
          height: 50px;
        }

        .header-nav {
          display: flex;
          align-items: center;
          gap: 4px;
        }

        .header-nav-item {
          background: transparent;
          border: none;
          color: var(--text-secondary);
          font-size: 0.9rem;
          font-weight: 500;
          padding: 8px 16px;
          border-radius: 8px;
          cursor: pointer;
          transition: all 0.2s ease;
        }

        .header-nav-item:hover {
          color: var(--text-primary);
          background: rgba(255, 255, 255, 0.05);
        }

        .header-nav-item.active {
          color: var(--accent-blue);
          background: rgba(59, 130, 246, 0.1);
        }

        .header-right {
          display: flex;
          align-items: center;
          gap: 12px;
        }

        .header-lang-btn {
          background: transparent;
          border: 1px solid var(--border-color);
          color: var(--text-secondary);
          font-size: 0.8rem;
          font-weight: 600;
          padding: 6px 12px;
          border-radius: 8px;
          cursor: pointer;
          transition: all 0.2s ease;
          letter-spacing: 0.5px;
        }

        .header-lang-btn:hover {
          border-color: var(--accent-blue);
          color: var(--accent-blue);
        }

        .header-user {
          display: flex;
          align-items: center;
          gap: 12px;
        }

        .header-user-info {
          display: flex;
          align-items: center;
          gap: 8px;
        }

        .header-nickname {
          color: var(--text-primary);
          font-size: 0.9rem;
          font-weight: 500;
        }

        .header-logout-btn {
          background: transparent;
          border: 1px solid var(--border-color);
          color: var(--text-muted);
          font-size: 0.8rem;
          padding: 6px 14px;
          border-radius: 8px;
          cursor: pointer;
          transition: all 0.2s ease;
        }

        .header-logout-btn:hover {
          border-color: var(--accent-red);
          color: var(--accent-red);
        }

        .header-login-btn {
          background: var(--gradient-primary);
          border: none;
          color: white;
          font-size: 0.85rem;
          font-weight: 600;
          padding: 8px 20px;
          border-radius: 8px;
          cursor: pointer;
          transition: all 0.2s ease;
        }

        .header-login-btn:hover {
          transform: translateY(-1px);
          box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
        }

        @media (max-width: 768px) {
          .header-nav {
            display: none;
          }
          .header-bar {
            padding: 0 16px;
          }
        }
      `}</style>
    </header>
  );
}