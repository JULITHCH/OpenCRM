import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import de from './de.json'
import en from './en.json'

// Default-Sprache Deutsch, Fallback Englisch (ADR-007: zweisprachige Oberflaeche).
// Eine Sprachumschaltung im UI folgt in einem spaeteren Inkrement.
void i18n.use(initReactI18next).init({
  resources: {
    de: { translation: de },
    en: { translation: en },
  },
  lng: 'de',
  fallbackLng: 'en',
  interpolation: {
    // React escaped selbst — doppeltes Escaping vermeiden.
    escapeValue: false,
  },
})

export default i18n
