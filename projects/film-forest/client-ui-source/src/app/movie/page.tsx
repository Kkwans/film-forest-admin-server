'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { movieApi } from '@/lib/api';
import { type Movie } from '@/stores/movieStore';

export default function MoviePage() {
  const [movies, setMovies] = useState<Movie[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [region, setRegion] = useState('all');
  const [year, setYear] = useState('all');

  useEffect(() => {
    fetchMovies();
  }, [region, year]);

  const fetchMovies = async () => {
    setLoading(true);
    try {
      const params: any = { page: 1, size: 20, type: 'movie' };
      if (region !== 'all') params.region = region;
      if (year !== 'all') {
        if (year === 'older') params.year = 0; // 表示更早
        else params.year = parseInt(year);
      }
      const res = await movieApi.list(params) as any;
      const raw = res.data?.data?.records || res.data?.data || res.data || [];
      // 映射后端字段 -> 前端字段
      const list = raw.map((m: any) => ({
        id: m.id,
        title: m.title,
        cover: m.posterUrl || m.cover,
        type: 'movie',
        year: m.year,
        region: Array.isArray(m.region) ? m.region[0] : (m.region || '未知'),
        rating: m.scoreDouban || m.score_imdb || m.rating,
        summary: m.storyline,
      }));
      setMovies(list);
    } catch {
      setMovies([
        { id: 1, title: '流浪地球3', cover: 'https://picsum.photos/seed/m1/300/450', type: 'movie', year: 2026, region: '大陆', rating: 9.2 },
        { id: 2, title: '满江红2', cover: 'https://picsum.photos/seed/m2/300/450', type: 'movie', year: 2026, region: '大陆', rating: 8.8 },
        { id: 3, title: '哪吒之魔童降世', cover: 'https://picsum.photos/seed/m3/300/450', type: 'movie', year: 2025, region: '大陆', rating: 9.0 },
        { id: 4, title: '热辣滚烫', cover: 'https://picsum.photos/seed/m4/300/450', type: 'movie', year: 2025, region: '大陆', rating: 8.5 },
        { id: 5, title: '第二人生', cover: 'https://picsum.photos/seed/m5/300/450', type: 'movie', year: 2026, region: '大陆', rating: 8.7 },
        { id: 6, title: '星际穿越2', cover: 'https://picsum.photos/seed/m6/300/450', type: 'movie', year: 2026, region: '美国', rating: 9.1 },
        { id: 7, title: '沙丘2', cover: 'https://picsum.photos/seed/m7/300/450', type: 'movie', year: 2024, region: '美国', rating: 8.6 },
        { id: 8, title: '奥本海默', cover: 'https://picsum.photos/seed/m8/300/450', type: 'movie', year: 2023, region: '美国', rating: 8.9 },
      ]);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (keyword.trim()) {
      window.location.href = `/search?q=${encodeURIComponent(keyword.trim())}`;
    }
  };

  return (
    <div className="flex flex-col gap-6">
      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">电影</h1>
          <p className="text-[var(--text-secondary)] text-sm mt-1">发现最新最热电影资源</p>
        </div>
        <form onSubmit={handleSearch} className="flex items-center gap-2">
          <Input
            placeholder="搜索电影..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="w-full sm:w-48 bg-[var(--bg-primary)] border-[var(--border-color)] text-[var(--text-primary)] placeholder:text-[var(--text-muted)]"
          />
          <button type="submit" className="px-4 py-2 bg-[var(--accent)] hover:bg-[var(--accent-hover)] text-white rounded-lg text-sm">
            搜索
          </button>
        </form>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3">
        <Select value={region} onValueChange={(v) => setRegion(v || 'all')}>
          <SelectTrigger className="w-32 bg-[var(--bg-primary)] border-[var(--border-color)] text-[var(--text-primary)]">
            <SelectValue placeholder="地区" />
          </SelectTrigger>
          <SelectContent className="bg-[var(--bg-card)] border-[var(--border-color)] text-[var(--text-primary)]">
            <SelectItem value="all">全部地区</SelectItem>
            <SelectItem value="大陆">大陆</SelectItem>
            <SelectItem value="美国">美国</SelectItem>
            <SelectItem value="日本">日本</SelectItem>
            <SelectItem value="韩国">韩国</SelectItem>
            <SelectItem value="香港">香港</SelectItem>
          </SelectContent>
        </Select>
        <Select value={year} onValueChange={(v) => setYear(v || 'all')}>
          <SelectTrigger className="w-32 bg-[var(--bg-primary)] border-[var(--border-color)] text-[var(--text-primary)]">
            <SelectValue placeholder="年代" />
          </SelectTrigger>
          <SelectContent className="bg-[var(--bg-card)] border-[var(--border-color)] text-[var(--text-primary)]">
            <SelectItem value="all">全部年代</SelectItem>
            <SelectItem value="2026">2026</SelectItem>
            <SelectItem value="2025">2025</SelectItem>
            <SelectItem value="2024">2024</SelectItem>
            <SelectItem value="2023">2023</SelectItem>
            <SelectItem value="older">更早</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* Grid */}
      {loading ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
          {Array.from({ length: 10 }).map((_, i) => (
            <div key={i} className="aspect-[2/3] rounded-lg bg-[var(--bg-card)] animate-pulse" />
          ))}
        </div>
      ) : movies.length === 0 ? (
        <div className="text-center py-12 text-[var(--text-secondary)]">暂无电影数据</div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
          {movies.map((movie) => (
            <Link key={movie.id} href={`/movie/${movie.id}`} className="group">
              <Card className="overflow-hidden bg-[var(--bg-card)] border-[var(--border-color)] group-hover:border-[var(--accent)]/50 transition-all">
                <div className="relative aspect-[2/3] overflow-hidden">
                  <img
                    src={movie.cover || `https://picsum.photos/seed/m${movie.id}/300/450`}
                    alt={movie.title}
                    className="object-cover w-full h-full group-hover:scale-105 transition-transform duration-300"
                  />
                  {movie.rating && (
                    <Badge className="absolute top-2 right-2 bg-[var(--accent)] text-white text-xs">
                      {movie.rating}
                    </Badge>
                  )}
                </div>
                <CardContent className="p-3">
                  <p className="font-medium text-sm text-[var(--text-primary)] truncate group-hover:text-[var(--accent)] transition-colors">
                    {movie.title}
                  </p>
                  <p className="text-xs text-[var(--text-muted)] mt-1">
                    {movie.year} · {movie.region || '未知'}
                  </p>
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}