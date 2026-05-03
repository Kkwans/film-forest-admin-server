import { create } from 'zustand';

export interface Movie {
  id: number;
  title: string;
  cover: string;
  type: string;
  year: number;
  region: string;
  rating?: number;
  summary?: string;
  status?: string;
  views?: number;
  createdAt?: string;
}

interface MovieStore {
  movies: Movie[];
  loading: boolean;
  error: string | null;
  setMovies: (movies: Movie[]) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
}

export const useMovieStore = create<MovieStore>((set) => ({
  movies: [],
  loading: false,
  error: null,
  setMovies: (movies) => set({ movies }),
  setLoading: (loading) => set({ loading }),
  setError: (error) => set({ error }),
}));