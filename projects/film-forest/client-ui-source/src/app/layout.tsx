import type { Metadata, Viewport } from "next";
import { Noto_Sans_SC } from "next/font/google";
import "./globals.css";
import Header from "@/components/Header";
import Footer from "@/components/Footer";

const notoSans = Noto_Sans_SC({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "影视森林",
  description: "影视资源聚合平台 - 电影/剧集/综艺/动漫/短剧",
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN" className="h-full">
      <body className={`${notoSans.className} min-h-full flex flex-col bg-[var(--bg-primary)] text-[var(--text-primary)]`}>
        <Header />
        <main className="flex-1 container px-4 mx-auto max-w-7xl py-6">
          {children}
        </main>
        <Footer />
      </body>
    </html>
  );
}