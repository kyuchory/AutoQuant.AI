import { create } from 'zustand';

interface LanguageState {
  language: 'ko' | 'en';
  setLanguage: (lang: 'ko' | 'en') => void;
}

export const useLanguageStore = create<LanguageState>((set) => ({
  language: 'ko',
  setLanguage: (language) => {
    localStorage.setItem('i18nextLng', language);
    set({ language });
  },
}));