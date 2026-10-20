/** Identity and display data committed with a track load. */
export interface PlaybackMetadata {
  songId?: number;
  title: string;
  artist: string;
  album: string;
  coverUrl: string;
  durationMs: number;
  canLike?: boolean;
}
