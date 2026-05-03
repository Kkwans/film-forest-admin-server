import Link from 'next/link';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';

const CATEGORIES = [
  { label: '电影', href: '/movie', emoji: '🎬', desc: '最新电影大片' },
  { label: '剧集', href: '/drama', emoji: '📺', desc: '热播电视剧' },
  { label: '综艺', href: '/variety', emoji: '🎤', desc: '热门综艺节目' },
  { label: '动漫', href: '/anime', emoji: '🎯', desc: '最新动漫番剧' },
  { label: '短剧', href: '/short', emoji: '⚡', desc: '精彩短剧集锦' },
];

const HOT_MOVIES = [
  { id: 1, title: '流浪地球3', cover: 'https://picsum.photos/seed/m1/300/450', rating: 9.2, year: 2026 },
  { id: 2, title: '满江红2', cover: 'https://picsum.photos/seed/m2/300/450', rating: 8.8, year: 2026 },
  { id: 3, title: '哪吒之魔童降世', cover: 'https://picsum.photos/seed/m3/300/450', rating: 9.0, year: 2025 },
  { id: 4, title: '热辣滚烫', cover: 'https://picsum.photos/seed/m4/300/450', rating: 8.5, year: 2025 },
  { id: 5, title: '第二人生', cover: 'https://picsum.photos/seed/m5/300/450', rating: 8.7, year: 2026 },
  { id: 6, title: '星际穿越2', cover: 'https://picsum.photos/seed/m6/300/450', rating: 9.1, year: 2026 },
];

export default function HomePage() {
  return (
    <div className="flex flex-col gap-10">
      {/* Hero */}
      <section className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-emerald-500/10 via-[var(--bg-secondary)] to-[var(--bg-card)] border border-[var(--border-color)]">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,_var(--tw-gradient-stops))] from-emerald-500/10 via-transparent to-transparent" />
        <div className="relative px-6 py-10 md:px-16 md:py-24">
          <div className="max-w-2xl">
            <Badge className="mb-4 bg-[var(--accent)]/20 text-[var(--accent)] border-[var(--accent)]/30">
              影视资源聚合平台
            </Badge>
            <h1 className="text-3xl md:text-5xl font-bold text-[var(--text-primary)] mb-4 leading-tight">
              发现精彩
              <span className="text-[var(--accent)]">影视世界</span>
            </h1>
            <p className="text-base md:text-lg text-[var(--text-secondary)] mb-6 md:mb-8">
              聚合全网优质影视资源，电影、剧集、综艺、动漫一网打尽
            </p>
            <div className="flex flex-col sm:flex-row gap-3">
              <Link
                href="/movie"
                className="inline-flex items-center justify-center px-6 py-3 rounded-full bg-[var(--accent)] hover:bg-[var(--accent-hover)] text-white font-medium transition-colors text-sm"
              >
                探索电影
              </Link>
              <Link
                href="/search"
                className="inline-flex items-center justify-center px-6 py-3 rounded-full border border-[var(--border-color)] hover:border-[var(--accent)] text-[var(--text-secondary)] hover:text-[var(--text-primary)] font-medium transition-colors text-sm"
              >
                搜索影视
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Categories */}
      <section>
        <h2 className="text-xl font-semibold text-[var(--text-primary)] mb-4">影视分类</h2>
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3">
          {CATEGORIES.map((cat) => (
            <Link
              key={cat.href}
              href={cat.href}
              className="group flex flex-col items-center gap-2 p-5 rounded-xl bg-[var(--bg-card)] border border-[var(--border-color)] hover:border-[var(--accent)]/50 hover:bg-[var(--bg-secondary)] transition-all"
            >
              <span className="text-3xl">{cat.emoji}</span>
              <span className="font-medium text-[var(--text-primary)] group-hover:text-[var(--accent)] transition-colors">
                {cat.label}
              </span>
              <span className="text-xs text-[var(--text-muted)]">{cat.desc}</span>
            </Link>
          ))}
        </div>
      </section>

      {/* Hot Movies */}
      <section>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-semibold text-[var(--text-primary)]">热门推荐</h2>
          <Link href="/movie" className="text-sm text-[var(--accent)] hover:text-[var(--accent-hover)] transition-colors">
            查看全部 →
          </Link>
        </div>
        <div className="grid grid-cols-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3 md:gap-4">
          {HOT_MOVIES.map((movie) => (
            <Link key={movie.id} href={`/movie/${movie.id}`} className="group">
              <Card className="overflow-hidden bg-[var(--bg-card)] border-[var(--border-color)] group-hover:border-[var(--accent)]/50 transition-all">
                <div className="relative aspect-[2/3] overflow-hidden">
                  <img
                    src={movie.cover}
                    alt={movie.title}
                    className="object-cover w-full h-full group-hover:scale-105 transition-transform duration-300"
                  />
                  <Badge className="absolute top-2 right-2 bg-[var(--accent)] text-white text-xs">
                    {movie.rating}
                  </Badge>
                </div>
                <CardContent className="p-3">
                  <p className="font-medium text-sm text-[var(--text-primary)] truncate group-hover:text-[var(--accent)] transition-colors">
                    {movie.title}
                  </p>
                  <p className="text-xs text-[var(--text-muted)] mt-1">{movie.year}</p>
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}