"""Emacs client for executing elisp functions via emacsclient."""

import json
import logging
import os
import subprocess
from typing import Any, Dict, List, Optional, Union

logger = logging.getLogger(__name__)


class EmacsClientError(Exception):
    """Raised when emacsclient operation fails."""
    pass


class EmacsClient:
    """Client for communicating with Emacs via emacsclient."""
    
    def __init__(self, server_file: Optional[str] = None):
        """Initialize EmacsClient.
        
        Args:
            server_file: Path to the Emacs server file (defaults to EMACS_SERVER_FILE env var or ~/emacs-server/server)
        """
        if server_file:
            self.server_file = server_file
        else:
            # Check for environment variable first, then fall back to HOME
            env_server_file = os.environ.get('EMACS_SERVER_FILE')
            if env_server_file:
                self.server_file = env_server_file
                logger.info(f"Using EMACS_SERVER_FILE environment variable: {self.server_file}")
            else:
                self.server_file = os.path.expanduser("~/emacs-server/server")
                logger.info(f"Using default HOME-based path: {self.server_file}")
        
        self.timeout = 30  # seconds
        
        # Log the resolved server file path for debugging
        logger.info(f"EmacsClient initialized with server file: {self.server_file}")
        logger.info(f"Server file exists: {os.path.exists(self.server_file)}")
        if os.path.exists(self.server_file):
            logger.info(f"Server file permissions: {oct(os.stat(self.server_file).st_mode)}")
        else:
            logger.warning(f"Server file does not exist: {self.server_file}")
        logger.info(f"Current user HOME: {os.path.expanduser('~')}")
        logger.info(f"Current working directory: {os.getcwd()}")
    
    def _escape_for_elisp(self, value: str) -> str:
        """Escape string for safe elisp evaluation.
        
        Args:
            value: String to escape
            
        Returns:
            Escaped string safe for elisp
        """
        if not isinstance(value, str):
            value = str(value)

        return (value
                .replace("\\", "\\\\")
                .replace('"', '\\"')
                .replace('\n', '\\n')
                .replace('\t', '\\t'))
    
    def _build_elisp_list(self, items: List[str]) -> str:
        """Build elisp list from Python list.
        
        Args:
            items: List of strings
            
        Returns:
            Elisp list representation
        """
        escaped_items = [f'"{self._escape_for_elisp(item)}"' for item in items]
        return f"(list {' '.join(escaped_items)})"
    
    def _execute_command(self, command: str) -> str:
        """Execute emacsclient command.
        
        Args:
            command: Shell command to execute
            
        Returns:
            Command output
            
        Raises:
            EmacsClientError: If command fails
        """
        try:
            logger.info(f"Executing command: {command}")
            logger.info(f"Server file path: {self.server_file}")
            logger.info(f"Server file exists: {os.path.exists(self.server_file)}")
            
            result = subprocess.run(
                command,
                shell=True,
                capture_output=True,
                text=True,
                timeout=self.timeout,
                check=False
            )
            
            logger.info(f"Command return code: {result.returncode}")
            logger.info(f"Command stderr: {result.stderr}")
            logger.info(f"Command stdout: {result.stdout}")
            
            if result.returncode != 0:
                error_msg = f"emacsclient failed with code {result.returncode}: {result.stderr}"
                logger.error(error_msg)
                raise EmacsClientError(error_msg)
            
            logger.debug(f"Command output: {result.stdout}")
            return result.stdout.strip()
            
        except subprocess.TimeoutExpired as e:
            error_msg = f"emacsclient timed out after {self.timeout} seconds"
            logger.error(error_msg)
            raise EmacsClientError(error_msg) from e
        except Exception as e:
            error_msg = f"Failed to execute emacsclient: {e}"
            logger.error(error_msg)
            raise EmacsClientError(error_msg) from e
    
    def _parse_json_response(self, response: str) -> Dict[str, Any]:
        """Parse JSON response from elisp function.
        
        Args:
            response: Raw response string
            
        Returns:
            Parsed JSON data
            
        Raises:
            EmacsClientError: If JSON parsing fails
        """
        try:
            # Handle case where response might be a character array
            parsed = json.loads(response)
            
            # Check if we got a character array (keys are numbers)
            if isinstance(parsed, dict) and all(key.isdigit() for key in parsed.keys()):
                # Reconstruct string from character array
                reconstructed = ''.join(parsed[str(i)] for i in sorted(int(k) for k in parsed.keys()))
                parsed = json.loads(reconstructed)
            
            return parsed
        except json.JSONDecodeError as e:
            error_msg = f"Failed to parse JSON response: {e}. Raw response: {response[:500]}"
            logger.error(error_msg)
            raise EmacsClientError(error_msg) from e
    
    def eval_elisp(self, expression: str) -> Dict[str, Any]:
        """Evaluate elisp expression and return parsed JSON response.
        
        Args:
            expression: Elisp expression to evaluate
            
        Returns:
            Parsed JSON response from elisp function
            
        Raises:
            EmacsClientError: If evaluation fails
        """
        # Escape the elisp expression for shell
        safe_expression = (expression
                          .replace("\\", "\\\\")
                          .replace('"', '\\"')
                          .replace('`', '\\`')
                          .replace('$', '\\$'))
        
        command = f'emacsclient --server-file={self.server_file} -e "{safe_expression}"'
        
        response = self._execute_command(command)
        return self._parse_json_response(response)
    
    def contextual_search(self, query: str, limit: int = 10) -> Dict[str, Any]:
        """Perform contextual search in org-roam.

        Args:
            query: Search query
            limit: Maximum number of results

        Returns:
            Search results with enhanced context
        """
        expression = f'(my/api-contextual-search "{self._escape_for_elisp(query)}" {limit})'
        return self.eval_elisp(expression)

    def semantic_search(self, query: str, limit: int = 10, cutoff: float = 0.55) -> Dict[str, Any]:
        """Perform semantic vector search using org-roam-semantic.

        Args:
            query: Search query
            limit: Maximum number of results
            cutoff: Similarity threshold (0.0-1.0)

        Returns:
            Semantically similar notes with full content and similarity scores
        """
        expression = f'(my/api-semantic-search "{self._escape_for_elisp(query)}" {limit} {cutoff})'
        return self.eval_elisp(expression)
    
    def search_notes(self, query: str) -> Dict[str, Any]:
        """Perform basic search in org-roam.
        
        Args:
            query: Search query
            
        Returns:
            Basic search results
        """
        expression = f'(my/api-search-notes "{self._escape_for_elisp(query)}")'
        return self.eval_elisp(expression)
    
    def create_note(
        self,
        title: str,
        content: str,
        note_type: str = "reference",
        confidence: str = "medium",
        url: Optional[str] = None,
        metadata: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """Create a new org-roam note with optional structured formatting.

        Args:
            title: Note title
            content: Note content (can be plain text or structured)
            note_type: Type of note (reference, video, etc.)
            confidence: Confidence level (high, medium, low)
            url: Optional URL for video/web content
            metadata: Optional metadata for structured content

        Returns:
            Creation result with note details
        """
        # If this is a video note with URL and metadata, format it specially
        if note_type == "video" and url:
            content = self._format_video_content(content, url, metadata or {})

        expression = (f'(my/api-create-note "{self._escape_for_elisp(title)}" '
                     f'"{self._escape_for_elisp(content)}" '
                     f'"{self._escape_for_elisp(note_type)}" '
                     f'"{self._escape_for_elisp(confidence)}")')
        return self.eval_elisp(expression)

    def _format_video_content(
        self,
        transcript: str,
        url: str,
        metadata: Dict[str, Any]
    ) -> str:
        """Format video content with structured layout.

        Args:
            transcript: Organized transcript content
            url: Video URL
            metadata: Video metadata

        Returns:
            Formatted note content
        """
        from datetime import datetime
        current_time = datetime.now()
        date_str = current_time.strftime("%Y-%m-%d")
        time_str = current_time.strftime("%H:%M")

        # Build structured note content
        note_content = f"""* Video Information
- *URL:* {url}
- *Captured:* {date_str} {time_str}
- *Type:* YouTube Video
"""

        # Add metadata if provided
        if metadata:
            for key, value in metadata.items():
                if value:  # Only add non-empty values
                    note_content += f"- *{key.title()}:* {value}\n"

        note_content += f"""
-----

{transcript}

-----
/Note created from YouTube video transcript using org-roam-mcp/"""

        return note_content
    
    def add_daily_entry(
        self,
        timestamp: str,
        title: str,
        points: List[str],
        next_steps: Optional[List[str]] = None,
        tags: Optional[List[str]] = None,
        entry_type: str = "journal"
    ) -> Dict[str, Any]:
        """Add structured entry to daily note.
        
        Args:
            timestamp: Entry timestamp (HH:MM format)
            title: Entry title
            points: Main points/content
            next_steps: Optional action items
            tags: Optional tags
            entry_type: "journal" or "todo" - determines formatting
            
        Returns:
            Result of adding entry
        """
        next_steps = next_steps or []
        tags = tags or []
        
        # Format title based on entry type
        if entry_type.lower() == "todo":
            formatted_title = f"TODO {title}"
        else:
            formatted_title = title
            
        points_list = self._build_elisp_list(points)
        steps_list = self._build_elisp_list(next_steps) 
        tags_list = self._build_elisp_list(tags)
        
        expression = (f'(my/add-daily-entry-structured "{self._escape_for_elisp(timestamp)}" '
                     f'"{self._escape_for_elisp(formatted_title)}" {points_list} {steps_list} {tags_list})')
        
        # This function returns nil on success, so we handle it specially
        try:
            self.eval_elisp(expression)
            return {"success": True, "message": "Daily entry added successfully"}
        except EmacsClientError as e:
            if "nil" in str(e).lower():
                return {"success": True, "message": "Daily entry added successfully"}
            raise
    
    def get_daily_content(self, date: Optional[str] = None) -> Dict[str, Any]:
        """Get content of daily note.
        
        Args:
            date: Date in YYYY-MM-DD format (defaults to today)
            
        Returns:
            Daily note content
        """
        if date:
            expression = f'(my/get-daily-note-content "{date}")'
        else:
            expression = '(my/get-daily-note-content)'
        
        # This returns raw content, not JSON
        try:
            command = f'emacsclient --server-file={self.server_file} -e "{expression}"'
            response = self._execute_command(command)
            
            # Clean the response - it may have literal \n characters
            content = response.replace('\\n', '\n').strip()
            if content.startswith('"') and content.endswith('"'):
                content = content[1:-1]  # Remove surrounding quotes
            
            return {
                "success": True,
                "content": content,
                "date": date or "today"
            }
        except Exception as e:
            logger.error(f"Failed to get daily content: {e}")
            return {
                "success": False,
                "error": str(e),
                "content": "",
                "date": date or "today"
            }
    

    def sync_database(self, force: bool = True) -> Dict[str, Any]:
        """Sync org-roam database.

        Args:
            force: Whether to force sync

        Returns:
            Sync result
        """
        force_param = "\\'force" if force else "nil"
        expression = f"(org-roam-db-sync {force_param})"

        try:
            self.eval_elisp(expression)
            return {"success": True, "message": "Database synced successfully"}
        except EmacsClientError as e:
            if "nil" in str(e).lower():
                return {"success": True, "message": "Database synced successfully"}
            logger.error(f"Database sync failed: {e}")
            return {"success": False, "error": str(e)}