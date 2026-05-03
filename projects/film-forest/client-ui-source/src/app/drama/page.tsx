'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { dramaApi } from '@/lib/api';

interface Drama {
  id: number;
  title: string;
  cover: string;
  year: number;
  region: string;
  rating?: number;
  episodes?: number;
  status?: string;
}

export default function DramaPage() {
  const [dramas, setDramas] = useState<Drama[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [region, setRegion] = useState('all');
  const [year, setYear] = useState('all');

  useEffect(() => {
    fetchDramas();
  }, [region, year]);

  const fetchDramas = async () => {
    setLoading(true);
    try {
      const params: any = { page: 1, size: 20 };
      if (region !== 'all') params.region = region;
      if (year !== 'all') {
        if (year === 'older') params.year = 0;
        else params.year = parseInt(year);
      }
      const res = await dramaApi.list(params) as any;
      const raw = res.data?.data?.records || res.data?.data || [];
      const list = raw.map((d: any) => ({
        id: d.id,
        title: d.title,
        cover: d.posterUrl,
        year: d.year,
        region: Array.isArray(d.region) ? d.region[0] : (d.region || '未知'),
        rating: d.scoreDouban,
        episodes: d.totalEpisode,
        status: d.status === 1 ? '更新中' : '已完结',
      }));
      setDramas(list);
    } catch (e) {
      console.error(e);
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
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">剧集</h1>
          <p className="text-[var(--text-secondary)] text-sm mt-1">最新热门电视剧，追剧不停</p>
        </div>
        <form onSubmit={handleSearch} className="flex items-center gap-2">
          <Input
            placeholder="搜索剧集..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="w-48 bg-[var(--bg-primary)] border-[var(--border-color)] text-[var(--text-primary)] placeholder:text-[var(--text-muted)]"
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
      ) : dramas.length === 0 ? (
        <div className="text-center py-12 text-[var(--text-secondary)]">暂无剧集数据</div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
          {dramas.map((drama) => (
            <Link key={drama.id} href={`/drama/${drama.id}`} className="group">
              <Card className="overflow-hidden bg-[var(--bg-card)] border-[var(--border-color)] group-hover:border-[var(--accent)]/50 transition-all">
                <div className="relative aspect-[2/3] overflow-hidden">
                  <img
                    src={drama.cover || `https://picsum.photos/seed/d${drama.id}/300/450`}
                    alt={drama.title}
                    className="object-cover w-full h-full group-hover:scale-105 transition-transform duration-300"
                  />
                  {drama.rating && (
                    <Badge className="absolute top-2 right-2 bg-[var(--accent)] text-white text-xs">
                      {drama.rating}
                    </Badge>
                  )}
                  {drama.status === '更新中' && (
                    <Badge className="absolute top-2 left-2 bg-amber-500 text-white text-xs">
                      更新中
                    </Badge>
                  )}
                </div>
                <CardContent className="p-3">
                  <p className="font-medium text-sm text-[var(--text-primary)] truncate group-hover:text-[var(--accent)] transition-colors">
                    {drama.title}
                  </p>
                  <p className="text-xs text-[var(--text-muted)] mt-1">
                    {drama.year} · {drama.region} · {drama.episodes}集
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