import type { Metadata, Viewport } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Zayniddin AI Call Assistant — Aqlli Telefon Yordamchisi',
  description: 'Zayniddin uchun 2 ta SIM karta qo\'llab-quvvatlovchi, OpenAI bilan ishlaydigan avtomatik qo\'ng\'iroq yordamchisi',
  manifest: '/manifest.json',
};

export const viewport: Viewport = {
  themeColor: '#4f46e5',
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="uz">
      <body className="antialiased min-h-screen selection:bg-indigo-500 selection:text-white">
        {children}
      </body>
    </html>
  );
}
