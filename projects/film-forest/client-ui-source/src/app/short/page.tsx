'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { shortDramaApi } from '@/lib/api';

interface ShortDramaItem {
  id: number;
  title: string;
  cover: string;
  year: number;
  region: string;
  totalEpisode?: number;
  duration?: number;
  status?: string;
}

export default function ShortDramaPage() {
  const [items, setItems] = useState<ShortDramaItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [region, setRegion] = useState('all');
  const [year, setYear] = useState('all');

  useEffect(() => {
    fetchList();
  }, [region, year]);

  const fetchList = async () => {
    setLoading(true);
    try {
      const params: any = { page: 1, size: 20 };
      if (region !== 'all') params.region = region;
      if (year !== 'all') {
        if (year === 'older') params.year = 0;
        else params.year = parseInt(year);
      }
      const res = await shortDramaApi.list(params) as any;
      const raw = res.data?.data?.records || res.data?.data || res.data || [];
      const list = raw.map((s: any) => ({
        id: s.id,
        title: s.title,
        cover: s.posterUrl,
        year: s.year,
        region: Array.isArray(s.region) ? s.region[0] : (s.region || '未知'),
        totalEpisode: s.totalEpisode,
        duration: s.duration,
        status: s.status === 1 ? '更新中' : '已完结',
      }));
      setItems(list);
    } catch {
      setItems([]);
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
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">短剧</h1>
          <p className="text-[var(--text-secondary)] text-sm mt-1">精彩短剧，碎片时间追不停</p>
        </div>
        <form onSubmit={handleSearch} className="flex items-center gap-2">
          <Input
            placeholder="搜索短剧..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="w-48 bg-[var(--bg-primary)] border-[var(--border-color)] text-[var(--text-primary)] placeholder:text-[var(--text-muted)]"
          />
          <button type="submit" className="px-4 py-2 bg-[var(--accent)] hover:bg-[var(--accent-hover)] text-white rounded-lg text-sm">
            搜索
          </button>
        </form>
      </div>

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
            <SelectItem value="台湾">台湾</SelectItem>
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
            <SelectItem value="older">更早</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {loading ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
          {Array.from({ length: 10 }).map((_, i) => (
            <div key={i} className="aspect-[2/3] rounded-lg bg-[var(--bg-card)] animate-pulse" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <div className="text-center py-12 text-[var(--text-secondary)]">暂无短剧数据</div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
          {items.map((s) => (
            <Link key={s.id} href={`/short/${s.id}`} className="group">
              <Card className="overflow-hidden bg-[var(--bg-card)] border-[var(--border-color)] group-hover:border-[var(--accent)]/50 transition-all">
                <div className="relative aspect-[2/3] overflow-hidden">
                  <img
                    src={s.cover || `https://picsum.photos/seed/s${s.id}/300/450`}
                    alt={s.title}
                    className="object-cover w-full h-full group-hover:scale-105 transition-transform duration-300"
                  />
                  {s.status === '更新中' && (
                    <Badge className="absolute top-2 left-2 bg-amber-500 text-white text-xs">
                      更新中
                    </Badge>
                  )}
                  {s.duration && (
                    <Badge className="absolute bottom-2 right-2 bg-black/60 text-white text-xs">
                      {s.duration}分钟/集
                    </Badge>
                  )}
                </div>
                <CardContent className="p-3">
                  <p className="font-medium text-sm text-[var(--text-primary)] truncate group-hover:text-[var(--accent)] transition-colors">
                    {s.title}
                  </p>
                  <p className="text-xs text-[var(--text-muted)] mt-1">
                    {s.year} · {s.region || '未知'} · {s.totalEpisode || '-'}集
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