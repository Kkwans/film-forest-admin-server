'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { movieApi } from '@/lib/api';
import { useResource } from '@/hooks/useResource';
import { type Movie } from '@/stores/movieStore';

export default function MovieDetailPage() {
  const params = useParams();
  const id = Number(params.id);
  const [movie, setMovie] = useState<Movie | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'online' | 'magnet'>('online');

  const { onlineResources, magnetResources, loading: resourcesLoading } = useResource('movie', id);

  useEffect(() => {
    if (id) fetchDetail();
  }, [id]);

  const fetchDetail = async () => {
    setLoading(true);
    try {
      const res = await movieApi.detail(id) as any;
      const m = res.data?.data || res.data;
      if (m && m.id) {
        setMovie({
          id: m.id,
          title: m.title,
          cover: m.posterUrl,
          type: 'movie',
          year: m.year,
          region: Array.isArray(m.region) ? m.region[0] : (m.region || '未知'),
          rating: m.scoreDouban,
          summary: m.storyline,
          status: m.status === 1 ? '热映中' : '已下线',
        });
      } else {
        setMovie(null);
      }
    } catch {
      setMovie(null);
    } finally {
      setLoading(false);
    }
  };

  const copyMagnet = (url: string) => {
    navigator.clipboard.writeText(url).then(() => {
      // Simple feedback could be enhanced with a toast
    });
  };

  if (loading) {
    return (
      <div className="flex flex-col gap-6 animate-pulse">
        <div className="flex flex-col sm:flex-row gap-4 sm:gap-6">
          <div className="w-full sm:w-48 md:w-64 aspect-[2/3] rounded-xl bg-[var(--bg-card)] max-w-[192px] mx-auto sm:mx-0" />
          <div className="flex-1 flex flex-col gap-4 pt-2 sm:pt-0">
            <div className="h-8 w-48 bg-[var(--bg-card)] rounded" />
            <div className="h-4 w-32 bg-[var(--bg-card)] rounded" />
            <div className="h-20 bg-[var(--bg-card)] rounded mt-4" />
          </div>
        </div>
      </div>
    );
  }

  if (!movie) {
    return (
      <div className="text-center py-12">
        <p className="text-[var(--text-secondary)]">电影不存在</p>
        <Link href="/movie" className="text-[var(--accent)] hover:underline mt-4 inline-block">
          返回电影列表
        </Link>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Back */}
      <Link href="/movie" className="text-sm text-[var(--text-secondary)] hover:text-[var(--text-primary)] transition-colors flex items-center gap-1">
        ← 返回电影列表
      </Link>

      {/* Info */}
      <div className="flex gap-6 flex-col sm:flex-row">
        {/* Cover */}
        <div className="w-full sm:w-48 md:w-64 shrink-0 mx-auto sm:mx-0 max-w-[256px]">
          <img
            src={movie.cover || `https://picsum.photos/seed/m${id}/400/600`}
            alt={movie.title}
            className="w-full aspect-[2/3] object-cover rounded-xl"
          />
        </div>

        {/* Meta */}
        <div className="flex flex-col gap-3 flex-1 min-w-0">
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="text-xl sm:text-2xl font-bold text-[var(--text-primary)]">{movie.title}</h1>
            {movie.status && <Badge className="bg-[var(--accent)] text-white text-xs">{movie.status}</Badge>}
          </div>

          <div className="flex items-center gap-x-4 gap-y-1 text-sm text-[var(--text-secondary)] flex-wrap">
            {movie.year && <span>{movie.year}</span>}
            {movie.region && <span>{movie.region}</span>}
            {movie.rating && (
              <span className="text-[var(--accent)] font-medium">⭐ {movie.rating}</span>
            )}
            {movie.views && <span>👁 {movie.views.toLocaleString()}</span>}
          </div>

          {movie.summary && (
            <p className="text-[var(--text-secondary)] text-sm leading-relaxed mt-2">{movie.summary}</p>
          )}

          <div className="flex gap-2 mt-4 flex-wrap">
            {onlineResources.length > 0 && (
              <Button
                className="bg-[var(--accent)] hover:bg-[var(--accent-hover)] text-white text-sm px-4"
                onClick={() => setActiveTab('online')}
              >
                播放
              </Button>
            )}
            <Button variant="outline" className="border-[var(--border-color)] text-[var(--text-secondary)] hover:bg-[var(--bg-card)] text-sm px-4">
              收藏
            </Button>
          </div>
        </div>
      </div>

      {/* Resources */}
      <div>
        <div className="flex gap-4 mb-4 border-b border-[var(--border-color)] overflow-x-auto">
          <button
            onClick={() => setActiveTab('online')}
            className={`pb-2 text-sm font-medium transition-colors whitespace-nowrap ${
              activeTab === 'online'
                ? 'text-[var(--accent)] border-b-2 border-[var(--accent)]'
                : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
            }`}
          >
            在线播放 ({onlineResources.length})
          </button>
          <button
            onClick={() => setActiveTab('magnet')}
            className={`pb-2 text-sm font-medium transition-colors whitespace-nowrap ${
              activeTab === 'magnet'
                ? 'text-[var(--accent)] border-b-2 border-[var(--accent)]'
                : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
            }`}
          >
            磁力下载 ({magnetResources.length})
          </button>
        </div>

        {resourcesLoading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {[1, 2].map((i) => (
              <div key={i} className="h-16 rounded-lg bg-[var(--bg-card)] animate-pulse" />
            ))}
          </div>
        ) : activeTab === 'online' ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {onlineResources.length === 0 ? (
              <p className="text-[var(--text-secondary)] text-sm text-center py-8">暂无在线播放资源</p>
            ) : (
              onlineResources.map((r) => (
                <div key={r.id} className="flex items-center justify-between p-4 rounded-lg bg-[var(--bg-card)] border border-[var(--border-color)] hover:border-[var(--accent)]/30 transition-all">
                  <div className="flex items-center gap-3 min-w-0">
                    <span className="text-lg shrink-0">🎬</span>
                    <div className="min-w-0">
                      <p className="text-[var(--text-primary)] font-medium text-sm truncate">{r.sourceName}</p>
                      <p className="text-xs text-[var(--text-muted)]">点击播放</p>
                    </div>
                  </div>
                  <a
                    href={r.sourceUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="px-3 py-1.5 bg-[var(--accent)] hover:bg-[var(--accent-hover)] text-white rounded-lg text-xs shrink-0"
                  >
                    播放
                  </a>
                </div>
              ))
            )}
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {magnetResources.length === 0 ? (
              <p className="text-[var(--text-secondary)] text-sm text-center py-8">暂无磁力链接资源</p>
            ) : (
              magnetResources.map((r) => (
                <div key={r.id} className="flex items-center justify-between p-4 rounded-lg bg-[var(--bg-card)] border border-[var(--border-color)] hover:border-[var(--accent)]/30 transition-all gap-2">
                  <div className="flex items-center gap-2 min-w-0 flex-1">
                    <span className="text-lg shrink-0">🧲</span>
                    <div className="min-w-0 flex-1">
                      <p className="text-[var(--text-primary)] font-medium text-sm truncate">
                        {r.title || '磁力链接'}
                      </p>
                      <div className="flex items-center gap-1 flex-wrap mt-0.5">
                        {r.resolution && <Badge variant="outline" className="text-xs border-[var(--border-color)] text-[var(--text-muted)]">{r.resolution}</Badge>}
                        {r.hasSubtitle && <Badge variant="outline" className="text-xs border-[var(--border-color)]">字幕</Badge>}
                        {r.isSpecialSub && <Badge variant="outline" className="text-xs border-[var(--border-color)]">特效字幕</Badge>}
                      </div>
                    </div>
                  </div>
                  <button
                    onClick={() => copyMagnet(r.magnetUrl)}
                    className="px-3 py-1.5 bg-[var(--bg-primary)] hover:bg-[var(--bg-secondary)] border border-[var(--border-color)] text-[var(--text-primary)] rounded-lg text-xs shrink-0"
                  >
                    复制
                  </button>
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
}