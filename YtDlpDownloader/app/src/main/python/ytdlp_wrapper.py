"""
yt-dlp Python wrapper for Android
This module provides a clean interface to yt-dlp functionality
"""

import json
import os
import sys
import re
from typing import Dict, List, Optional, Callable, Any
import threading
import queue

# yt-dlp imports
import yt_dlp
from yt_dlp.utils import DownloadError


class ProgressHook:
    """Custom progress hook for tracking download progress"""
    
    def __init__(self, callback: Optional[Callable] = None):
        self.callback = callback
        self.last_progress = 0
        
    def __call__(self, d: Dict[str, Any]):
        if d['status'] == 'downloading':
            progress_data = {
                'status': 'downloading',
                'downloaded_bytes': d.get('downloaded_bytes', 0),
                'total_bytes': d.get('total_bytes') or d.get('total_bytes_estimate', 0),
                'speed': d.get('speed', 0),
                'eta': d.get('eta', 0),
                'percent': self._calculate_percent(d)
            }
            if self.callback:
                self.callback(progress_data)
                
        elif d['status'] == 'finished':
            progress_data = {
                'status': 'finished',
                'filename': d.get('filename', ''),
                'total_bytes': d.get('total_bytes', 0)
            }
            if self.callback:
                self.callback(progress_data)
    
    def _calculate_percent(self, d: Dict[str, Any]) -> float:
        downloaded = d.get('downloaded_bytes', 0)
        total = d.get('total_bytes') or d.get('total_bytes_estimate', 0)
        if total > 0:
            return (downloaded / total) * 100
        return 0


class YtdlpWrapper:
    """Wrapper class for yt-dlp functionality"""
    
    def __init__(self, download_dir: str):
        self.download_dir = download_dir
        self._active_downloads: Dict[str, Any] = {}
        self._cancelled: set = set()
        self._lock = threading.Lock()
        
    def extract_info(self, url: str, extract_flat: bool = False) -> Dict[str, Any]:
        """
        Extract video/playlist information without downloading
        
        Args:
            url: The URL to extract info from
            extract_flat: If True, extract playlist entries without full info
            
        Returns:
            Dictionary containing video/playlist information
        """
        ydl_opts = {
            'quiet': True,
            'no_warnings': True,
            'extract_flat': extract_flat,
            'skip_download': True,
        }
        
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                return self._sanitize_info(info)
        except Exception as e:
            return {'error': str(e), 'url': url}
    
    def get_available_formats(self, url: str) -> List[Dict[str, Any]]:
        """
        Get available formats for a video
        
        Args:
            url: The video URL
            
        Returns:
            List of available formats
        """
        info = self.extract_info(url)
        if 'error' in info:
            return []
        
        formats = info.get('formats', [])
        result = []
        
        for fmt in formats:
            format_info = {
                'format_id': fmt.get('format_id', ''),
                'ext': fmt.get('ext', ''),
                'quality': fmt.get('quality_label') or fmt.get('format_note', 'unknown'),
                'resolution': fmt.get('resolution', 'unknown'),
                'filesize': fmt.get('filesize') or fmt.get('filesize_approx'),
                'has_video': fmt.get('vcodec', 'none') != 'none',
                'has_audio': fmt.get('acodec', 'none') != 'none',
                'video_codec': fmt.get('vcodec'),
                'audio_codec': fmt.get('acodec'),
                'fps': fmt.get('fps'),
                'bitrate': fmt.get('tbr'),
            }
            result.append(format_info)
        
        return result
    
    def download(self, 
                 url: str,
                 download_id: str,
                 format_type: str = 'mp4',
                 quality: str = 'best',
                 progress_callback: Optional[Callable] = None) -> Dict[str, Any]:
        """
        Download a video/audio file
        
        Args:
            url: The URL to download
            download_id: Unique identifier for this download
            format_type: 'mp4' or 'mp3'
            quality: 'best', 'high', 'medium', or 'low'
            progress_callback: Callback function for progress updates
            
        Returns:
            Dictionary with download result
        """
        if download_id in self._cancelled:
            self._cancelled.discard(download_id)
            return {'success': False, 'error': 'Download cancelled by user'}
        
        # Build format string based on type and quality
        format_string = self._build_format_string(format_type, quality)
        
        # Progress hook
        progress_hook = ProgressHook(progress_callback)
        
        # Postprocessor for audio extraction if needed
        postprocessors = []
        if format_type.lower() == 'mp3':
            postprocessors.append({
                'key': 'FFmpegExtractAudio',
                'preferredcodec': 'mp3',
                'preferredquality': self._get_audio_quality(quality),
            })
        
        ydl_opts = {
            'format': format_string,
            'outtmpl': os.path.join(self.download_dir, '%(title)s.%(ext)s'),
            'progress_hooks': [progress_hook],
            'postprocessors': postprocessors,
            'noplaylist': False,
            'continuedl': True,
            'retries': 10,
            'fragment_retries': 10,
            'skip_unavailable_fragments': True,
            'keep_fragments': False,
            'buffersize': 1024 * 16,
            'http_chunk_size': 1024 * 1024,
            'quiet': True,
            'no_warnings': False,
        }
        
        try:
            with self._lock:
                self._active_downloads[download_id] = True
            
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=True)
                filename = ydl.prepare_filename(info)
                
                # Adjust filename for audio files
                if format_type.lower() == 'mp3':
                    filename = os.path.splitext(filename)[0] + '.mp3'
                
                with self._lock:
                    if download_id in self._cancelled:
                        self._cancelled.discard(download_id)
                        # Try to delete partial file
                        try:
                            if os.path.exists(filename):
                                os.remove(filename)
                        except:
                            pass
                        return {'success': False, 'error': 'Download cancelled'}
                
                return {
                    'success': True,
                    'filename': filename,
                    'title': info.get('title', 'Unknown'),
                    'duration': info.get('duration'),
                    'uploader': info.get('uploader'),
                }
                
        except DownloadError as e:
            return {'success': False, 'error': str(e)}
        except Exception as e:
            return {'success': False, 'error': f'Unexpected error: {str(e)}'}
        finally:
            with self._lock:
                self._active_downloads.pop(download_id, None)
    
    def cancel_download(self, download_id: str):
        """Mark a download for cancellation"""
        with self._lock:
            self._cancelled.add(download_id)
            self._active_downloads.pop(download_id, None)
    
    def is_valid_url(self, url: str) -> bool:
        """Check if URL is supported by yt-dlp"""
        extractors = yt_dlp.extractor.gen_extractors()
        for extractor in extractors:
            if extractor.suitable(url) and extractor.IE_NAME != 'generic':
                return True
        return False
    
    def _build_format_string(self, format_type: str, quality: str) -> str:
        """Build yt-dlp format string based on preferences"""
        if format_type.lower() == 'mp3':
            # For audio, get best audio only
            return 'bestaudio/best'
        
        # Video formats
        quality_map = {
            'best': 'bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best',
            'high': 'bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080][ext=mp4]/best',
            'medium': 'bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best',
            'low': 'bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480][ext=mp4]/best',
        }
        return quality_map.get(quality, quality_map['best'])
    
    def _get_audio_quality(self, quality: str) -> str:
        """Get audio quality setting for MP3"""
        quality_map = {
            'best': '320',
            'high': '256',
            'medium': '192',
            'low': '128',
        }
        return quality_map.get(quality, '192')
    
    def _sanitize_info(self, info: Dict[str, Any]) -> Dict[str, Any]:
        """Sanitize and simplify info dictionary"""
        if not info:
            return {}
        
        result = {
            'id': info.get('id', ''),
            'title': info.get('title', 'Unknown'),
            'description': info.get('description', ''),
            'duration': info.get('duration'),
            'uploader': info.get('uploader'),
            'upload_date': info.get('upload_date'),
            'view_count': info.get('view_count'),
            'thumbnail': info.get('thumbnail'),
            'webpage_url': info.get('webpage_url', ''),
            'extractor': info.get('extractor', ''),
        }
        
        # Check if it's a playlist
        entries = info.get('entries')
        if entries is not None:
            result['is_playlist'] = True
            result['playlist_count'] = len(list(entries)) if entries else 0
            result['playlist_title'] = info.get('title', 'Playlist')
        else:
            result['is_playlist'] = False
            result['formats'] = info.get('formats', [])
        
        return result


# Global wrapper instance (will be initialized from Kotlin)
_wrapper_instance: Optional[YtdlpWrapper] = None


def initialize(download_dir: str):
    """Initialize the wrapper with download directory"""
    global _wrapper_instance
    _wrapper_instance = YtdlpWrapper(download_dir)


def extract_info(url: str) -> str:
    """Extract video info - returns JSON string"""
    if _wrapper_instance is None:
        return json.dumps({'error': 'Wrapper not initialized'})
    
    result = _wrapper_instance.extract_info(url)
    return json.dumps(result)


def get_available_formats(url: str) -> str:
    """Get available formats - returns JSON string"""
    if _wrapper_instance is None:
        return json.dumps({'error': 'Wrapper not initialized'})
    
    result = _wrapper_instance.get_available_formats(url)
    return json.dumps(result)


def download(url: str, download_id: str, format_type: str, quality: str) -> str:
    """
    Start a download - returns JSON result
    This is a blocking call that runs the full download
    """
    if _wrapper_instance is None:
        return json.dumps({'success': False, 'error': 'Wrapper not initialized'})
    
    # Progress callback that prints to stdout (captured by Chaquopy)
    def progress_callback(data):
        print(f"PROGRESS:{json.dumps(data)}", flush=True)
    
    result = _wrapper_instance.download(
        url=url,
        download_id=download_id,
        format_type=format_type,
        quality=quality,
        progress_callback=progress_callback
    )
    return json.dumps(result)


def cancel_download(download_id: str) -> str:
    """Cancel an active download"""
    if _wrapper_instance is None:
        return json.dumps({'success': False, 'error': 'Wrapper not initialized'})
    
    _wrapper_instance.cancel_download(download_id)
    return json.dumps({'success': True})


def is_valid_url(url: str) -> str:
    """Check if URL is valid - returns JSON boolean"""
    if _wrapper_instance is None:
        return json.dumps({'valid': False, 'error': 'Wrapper not initialized'})
    
    is_valid = _wrapper_instance.is_valid_url(url)
    return json.dumps({'valid': is_valid})
