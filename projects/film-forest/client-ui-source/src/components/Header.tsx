'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Menu, X } from 'lucide-react';

const NAV_ITEMS = [
  { label: '首页', href: '/' },
  { label: '电影', href: '/movie' },
  { label: '剧集', href: '/drama' },
  { label: '综艺', href: '/variety' },
  { label: '动漫', href: '/anime' },
  { label: '短剧', href: '/short' },
];

export default function Header() {
  const [keyword, setKeyword] = useState('');
  const [darkMode, setDarkMode] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const stored = localStorage.getItem('theme');
    if (stored === 'dark' || (!stored && prefersDark)) {
      setDarkMode(true);
      document.documentElement.classList.add('dark');
    }
  }, []);

  const toggleDark = () => {
    const next = !darkMode;
    setDarkMode(next);
    if (next) {
      document.documentElement.classList.add('dark');
      localStorage.setItem('theme', 'dark');
    } else {
      document.documentElement.classList.remove('dark');
      localStorage.setItem('theme', 'light');
    }
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (keyword.trim()) {
      window.location.href = `/search?q=${encodeURIComponent(keyword.trim())}`;
    }
  };

  const closeMenu = () => setMenuOpen(false);

  return (
    <>
      <header className="sticky top-0 z-50 w-full border-b bg-[var(--bg-secondary)] backdrop-blur-md border-[var(--border-color)]">
        <div className="container flex items-center justify-between h-14 px-4 mx-auto max-w-7xl">
          {/* Logo */}
          <Link href="/" className="flex items-center gap-2 group shrink-0">
            <span className="text-2xl">🌲</span>
            <span className="text-lg font-bold text-[var(--text-primary)] group-hover:text-[var(--accent)] transition-colors">
              影视森林
            </span>
          </Link>

          {/* Desktop Nav */}
          <nav className="hidden md:flex items-center gap-4 lg:gap-6">
            {NAV_ITEMS.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className="text-sm text-[var(--text-secondary)] hover:text-[var(--text-primary)] transition-colors"
              >
                {item.label}
              </Link>
            ))}
          </nav>

          {/* Search + Dark Toggle (desktop) */}
          <div className="hidden md:flex items-center gap-2">
            <form onSubmit={handleSearch} className="flex items-center gap-2">
              <Input
                placeholder="搜索影视..."
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                className="w-40 lg:w-48 bg-[var(--bg-primary)] border-[var(--border-color)] text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:border-[var(--accent)]"
              />
              <Button type="submit" size="sm" className="bg-[var(--accent)] hover:bg-[var(--accent-hover)] text-white shrink-0">
                搜索
              </Button>
            </form>
            <button
              onClick={toggleDark}
              className="p-2 rounded-lg border border-[var(--border-color)] hover:bg-[var(--bg-primary)] transition-colors text-[var(--text-secondary)] shrink-0"
              title="切换深色模式"
            >
              {darkMode ? '☀️' : '🌙'}
            </button>
          </div>

          {/* Mobile: search + menu toggle */}
          <div className="flex md:hidden items-center gap-2">
            <form onSubmit={handleSearch} className="flex items-center gap-1">
              <Input
                placeholder="搜索..."
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                className="w-24 bg-[var(--bg-primary)] border-[var(--border-color)] text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:border-[var(--accent)] text-sm h-8"
              />
            </form>
            <button
              onClick={toggleDark}
              className="p-1.5 rounded-md border border-[var(--border-color)] hover:bg-[var(--bg-primary)] transition-colors text-[var(--text-secondary)] shrink-0"
              title="切换深色模式"
            >
              <span className="text-sm">{darkMode ? '☀️' : '🌙'}</span>
            </button>
            <button
              onClick={() => setMenuOpen(!menuOpen)}
              className="p-1.5 rounded-md border border-[var(--border-color)] hover:bg-[var(--bg-primary)] transition-colors text-[var(--text-secondary)] shrink-0"
              title="菜单"
            >
              {menuOpen ? <X size={18} /> : <Menu size={18} />}
            </button>
          </div>
        </div>
      </header>

      {/* Mobile Menu Overlay */}
      {menuOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/50 md:hidden"
          onClick={closeMenu}
        />
      )}

      {/* Mobile Menu Drawer */}
      <div
        className={`
          fixed top-0 right-0 z-50 h-full w-56 bg-[var(--bg-secondary)] border-l border-[var(--border-color)]
          transform transition-transform duration-300 ease-in-out
          md:hidden
          ${menuOpen ? 'translate-x-0' : 'translate-x-full'}
        `}
      >
        <div className="flex flex-col p-4 gap-1">
          <div className="flex items-center justify-between mb-4">
            <span className="text-base font-bold text-[var(--text-primary)]">导航菜单</span>
            <button onClick={closeMenu} className="p-1 rounded-md hover:bg-[var(--bg-primary)]">
              <X size={20} className="text-[var(--text-secondary)]" />
            </button>
          </div>
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              onClick={closeMenu}
              className="px-3 py-2.5 rounded-lg text-sm text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-primary)] transition-colors"
            >
              {item.label}
            </Link>
          ))}
        </div>
      </div>
    </>
  );
}