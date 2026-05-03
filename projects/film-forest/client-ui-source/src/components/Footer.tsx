'use client';

import Link from 'next/link';

export default function Footer() {
  return (
    <footer className="w-full py-6 mt-10 border-t bg-[var(--bg-secondary)] border-[var(--border-color)]">
      <div className="container px-4 mx-auto max-w-7xl">
        <div className="flex flex-col md:flex-row items-center justify-between gap-4 text-sm text-[var(--text-muted)]">
          <div className="flex items-center gap-2">
            <span>🌲</span>
            <span className="text-[var(--text-secondary)]">影视森林</span>
          </div>
          <div className="flex items-center gap-4">
            <Link href="/" className="hover:text-[var(--text-primary)] transition-colors">首页</Link>
            <Link href="/movie" className="hover:text-[var(--text-primary)] transition-colors">电影</Link>
            <Link href="/drama" className="hover:text-[var(--text-primary)] transition-colors">剧集</Link>
          </div>
          <p>© 2026 影视森林. 仅供学习交流.</p>
        </div>
      </div>
    </footer>
  );
}