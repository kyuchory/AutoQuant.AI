'use client';

import { useEffect, useState, ReactNode } from 'react';
import { useLanguageStore } from '@/lib/store/languageStore';
import '@/lib/i18n/config';

export default function I18nProvider({ children }: { children: ReactNode }) {
  const { language } = useLanguageStore();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    // i18n 초기화 후 언어 설정 적용
    import('@/lib/i18n/config').then(({ default: i18n }) => {
      i18n.changeLanguage(language);
      setReady(true);
    });
  }, [language]);

  if (!ready) return null;

  return <>{children}</>;
}