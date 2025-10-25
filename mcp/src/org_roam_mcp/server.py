"""MCP server for org-roam knowledge management."""

import asyncio
import logging
from typing import Any, Dict, List, Optional

from mcp.server import Server
from mcp.server.models import InitializationOptions
from mcp.types import ServerCapabilities
from starlette.applications import Starlette
from starlette.routing import Route
from starlette.middleware.cors import CORSMiddleware
from starlette.responses import JSONResponse, Response
from starlette.requests import Request
import json
from mcp.types import (
    CallToolRequest,
    CallToolResult,
    ListToolsRequest,
    ListToolsResult,
    TextContent,
    Tool,
)

from .emacs_client import EmacsClient, EmacsClientError

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Initialize Emacs client
emacs_client = EmacsClient()

# Create MCP server
app = Server("org-roam-mcp")


@app.list_tools()
async def list_tools() -> ListToolsResult:
    """List available MCP tools."""
    return ListToolsResult(
        tools=[
            Tool(
                name="contextual_search",
                description="Enhanced search with full context for RAG applications",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "Search terms to query the knowledge base"
                        },
                        "limit": {
                            "type": "integer", 
                            "description": "Maximum number of results to return",
                            "default": 10,
                            "minimum": 1,
                            "maximum": 50
                        }
                    },
                    "required": ["query"]
                }
            ),
            Tool(
                name="search_notes",
                description="Basic search across note titles and content",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "Search terms to query note titles and content"
                        }
                    },
                    "required": ["query"]
                }
            ),
            Tool(
                name="semantic_search",
                description="Semantic vector search using AI embeddings to find conceptually related notes",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "Search query to find semantically similar content"
                        },
                        "limit": {
                            "type": "integer",
                            "description": "Maximum number of results to return",
                            "default": 10,
                            "minimum": 1,
                            "maximum": 50
                        },
                        "cutoff": {
                            "type": "number",
                            "description": "Similarity threshold (0.0-1.0, higher = more similar)",
                            "default": 0.55,
                            "minimum": 0.0,
                            "maximum": 1.0
                        }
                    },
                    "required": ["query"]
                }
            ),
            Tool(
                name="create_note",
                description="Create a new org-roam note with optional structured formatting for videos",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "title": {
                            "type": "string",
                            "description": "Title for the new note"
                        },
                        "content": {
                            "type": "string",
                            "description": "Content for the new note (transcript for videos)"
                        },
                        "type": {
                            "type": "string",
                            "description": "Type of note (reference, video, concept, etc.)",
                            "default": "reference"
                        },
                        "confidence": {
                            "type": "string",
                            "description": "Confidence level (high, medium, low)",
                            "default": "medium"
                        },
                        "url": {
                            "type": "string",
                            "description": "Optional URL (required for video notes)"
                        },
                        "metadata": {
                            "type": "object",
                            "description": "Optional metadata for structured content (video duration, channel, etc.)",
                            "properties": {
                                "duration": {"type": "string"},
                                "channel": {"type": "string"},
                                "views": {"type": "string"},
                                "upload_date": {"type": "string"}
                            }
                        }
                    },
                    "required": ["title", "content"]
                }
            ),
            Tool(
                name="add_daily_entry",
                description="Add structured entry to daily note",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "timestamp": {
                            "type": "string",
                            "description": "Entry timestamp in HH:MM format"
                        },
                        "title": {
                            "type": "string",
                            "description": "Entry title"
                        },
                        "points": {
                            "type": "array",
                            "items": {"type": "string"},
                            "description": "Main points or content items"
                        },
                        "next_steps": {
                            "type": "array",
                            "items": {"type": "string"},
                            "description": "Optional action items or next steps"
                        },
                        "tags": {
                            "type": "array", 
                            "items": {"type": "string"},
                            "description": "Optional tags for categorization"
                        },
                        "entry_type": {
                            "type": "string",
                            "description": "Entry type: 'journal' (past tense) or 'todo' (future tense)",
                            "enum": ["journal", "todo"],
                            "default": "journal"
                        }
                    },
                    "required": ["timestamp", "title", "points"]
                }
            ),
            Tool(
                name="get_daily_content",
                description="Get content of daily note",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "date": {
                            "type": "string",
                            "description": "Date in YYYY-MM-DD format (defaults to today)",
                            "pattern": r"^\d{4}-\d{2}-\d{2}$"
                        }
                    },
                    "required": []
                }
            ),
            Tool(
                name="sync_database",
                description="Sync org-roam database to ensure latest data",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "force": {
                            "type": "boolean",
                            "description": "Whether to force database sync",
                            "default": True
                        }
                    },
                    "required": []
                }
            ),
            Tool(
                name="generate_embeddings",
                description="Generate semantic embeddings for org-roam notes using org-roam-semantic. Processes notes missing embeddings or with stale embeddings.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "force": {
                            "type": "boolean",
                            "description": "Regenerate embeddings even for notes that already have them",
                            "default": False
                        }
                    },
                    "required": []
                }
            )
        ]
    )


@app.call_tool()
async def call_tool(request: CallToolRequest) -> CallToolResult:
    """Handle tool calls."""
    try:
        tool_name = request.name
        arguments = request.arguments or {}
        
        logger.info(f"Calling tool: {tool_name} with arguments: {arguments}")
        
        if tool_name == "contextual_search":
            query = arguments["query"]
            limit = arguments.get("limit", 10)
            
            result = emacs_client.contextual_search(query, limit)
            
            return CallToolResult(
                content=[
                    TextContent(
                        type="text",
                        text=f"Contextual search results for '{query}':\n\n" + 
                             _format_contextual_search_results(result)
                    )
                ]
            )
            
        elif tool_name == "search_notes":
            query = arguments["query"]

            result = emacs_client.search_notes(query)

            return CallToolResult(
                content=[
                    TextContent(
                        type="text",
                        text=f"Search results for '{query}':\n\n" +
                             _format_basic_search_results(result)
                    )
                ]
            )

        elif tool_name == "semantic_search":
            query = arguments["query"]
            limit = arguments.get("limit", 10)
            cutoff = arguments.get("cutoff", 0.55)

            result = emacs_client.semantic_search(query, limit, cutoff)

            return CallToolResult(
                content=[
                    TextContent(
                        type="text",
                        text=f"Semantic search results for '{query}':\n\n" +
                             _format_semantic_search_results(result)
                    )
                ]
            )
            
        elif tool_name == "create_note":
            title = arguments["title"]
            content = arguments["content"]
            note_type = arguments.get("type", "reference")
            confidence = arguments.get("confidence", "medium")
            url = arguments.get("url")
            metadata = arguments.get("metadata", {})

            result = emacs_client.create_note(title, content, note_type, confidence, url, metadata)

            if result.get("success"):
                note_info = result.get("note", {})
                embedding_generated = result.get("embedding_generated", False)

                if note_type == "video":
                    response = f"🎥 Video note created successfully: '{title}'\n"
                    response += f"ID: {note_info.get('id', 'N/A')}\n"
                    response += f"File: {note_info.get('file', 'N/A')}\n"
                    if url:
                        response += f"URL: {url}\n"
                else:
                    response = f"✅ Note created successfully: '{title}'\n"
                    response += f"ID: {note_info.get('id', 'N/A')}\n"
                    response += f"File: {note_info.get('file', 'N/A')}\n"
                    response += f"Type: {note_type}, Confidence: {confidence}\n"

                # Add embedding status
                if embedding_generated:
                    response += f"🔍 Semantic embedding generated automatically"
                else:
                    response += f"ℹ️ Semantic embeddings not available (org-roam-semantic not loaded)"
            else:
                response = f"❌ Failed to create note: {result.get('message', 'Unknown error')}"

            return CallToolResult(
                content=[TextContent(type="text", text=response)]
            )
            
        elif tool_name == "add_daily_entry":
            timestamp = arguments["timestamp"]
            title = arguments["title"]
            points = arguments["points"]
            next_steps = arguments.get("next_steps", [])
            tags = arguments.get("tags", [])
            entry_type = arguments.get("entry_type", "journal")
            
            result = emacs_client.add_daily_entry(
                timestamp, title, points, next_steps, tags, entry_type
            )
            
            if result.get("success"):
                # Return the exact response format the agent expects to see
                entry_label = "TODO" if entry_type == "todo" else "JOURNAL"
                response = f"✅ **{entry_label}** added to daily note: {title}"
            else:
                response = f"❌ Failed to add daily entry: {result.get('error', 'Unknown error')}"
            
            return CallToolResult(
                content=[TextContent(type="text", text=response)]
            )
            
        elif tool_name == "get_daily_content":
            date = arguments.get("date")
            
            result = emacs_client.get_daily_content(date)
            
            if result.get("success"):
                content = result.get("content", "")
                date_str = result.get("date", "today")
                
                if content:
                    response = f"📝 Daily note content for {date_str}:\n\n{content}"
                else:
                    response = f"📝 No content found for daily note ({date_str})"
            else:
                response = f"❌ Failed to get daily content: {result.get('error', 'Unknown error')}"
            
            return CallToolResult(
                content=[TextContent(type="text", text=response)]
            )
            
        elif tool_name == "sync_database":
            force = arguments.get("force", True)

            result = emacs_client.sync_database(force)

            if result.get("success"):
                response = "✅ Org-roam database synced successfully"
            else:
                response = f"❌ Failed to sync database: {result.get('error', 'Unknown error')}"

            return CallToolResult(
                content=[TextContent(type="text", text=response)]
            )

        elif tool_name == "generate_embeddings":
            force = arguments.get("force", False)

            result = emacs_client.generate_embeddings(force)

            if result.get("success"):
                count = result.get("count", 0)
                response = f"✅ Generated {count} embeddings for org-roam notes"
                if count == 0:
                    response += "\n(All notes already have current embeddings)"
            else:
                response = f"❌ Failed to generate embeddings: {result.get('error', 'Unknown error')}"

            return CallToolResult(
                content=[TextContent(type="text", text=response)]
            )

        else:
            return CallToolResult(
                content=[
                    TextContent(
                        type="text",
                        text=f"Unknown tool: {tool_name}"
                    )
                ]
            )
            
    except EmacsClientError as e:
        logger.error(f"Emacs client error in {request.name}: {e}")
        return CallToolResult(
            content=[
                TextContent(
                    type="text",
                    text=f"❌ Emacs communication error: {str(e)}"
                )
            ]
        )
    except Exception as e:
        logger.error(f"Unexpected error in {request.name}: {e}")
        return CallToolResult(
            content=[
                TextContent(
                    type="text",
                    text=f"❌ Unexpected error: {str(e)}"
                )
            ]
        )


def _format_contextual_search_results(result: Dict[str, Any]) -> str:
    """Format contextual search results for display."""
    if not result.get("success"):
        return f"Search failed: {result.get('error', 'Unknown error')}"
    
    notes = result.get("notes", [])
    context = result.get("knowledge_context", {})
    
    if not notes:
        return "No relevant notes found."
    
    response = f"Found {len(notes)} relevant notes:\n\n"
    
    for i, note in enumerate(notes, 1):
        response += f"{i}. **{note.get('title', 'Untitled')}**\n"
        response += f"   Relevance: {note.get('relevance_score', 0):.3f}\n"
        
        # Show snippet of content
        content = note.get('full_content', '')
        if content:
            # Clean content and show first 200 chars
            clean_content = content.replace(':PROPERTIES:', '').replace(':END:', '')
            clean_content = ' '.join(clean_content.split()[:30])  # First 30 words
            response += f"   Content: {clean_content}...\n"
        
        # Show connections
        backlinks = note.get('backlinks', [])
        forward_links = note.get('forward_links', [])
        if backlinks or forward_links:
            response += f"   Connections: {len(backlinks)} backlinks, {len(forward_links)} forward links\n"
        
        response += "\n"
    
    # Add knowledge context summary
    total_connections = context.get('total_connections', 0)
    if total_connections > 0:
        response += f"Knowledge graph summary: {total_connections} total connections found\n"
    
    return response


def _format_semantic_search_results(result: Dict[str, Any]) -> str:
    """Format semantic search results for display."""
    if not result.get("success"):
        error_msg = result.get('error', 'Unknown error')
        if 'fallback_available' in result:
            return f"Semantic search failed: {error_msg}\n\nFallback to contextual search recommended."
        return f"Semantic search failed: {error_msg}"

    notes = result.get("notes", [])
    context = result.get("knowledge_context", {})

    if not notes:
        return "No semantically similar notes found."

    search_type = result.get("search_type", "unknown")
    cutoff = result.get("similarity_cutoff", 0.55)

    response = f"Found {len(notes)} semantically similar notes (similarity >= {cutoff:.2f}):\n\n"

    for i, note in enumerate(notes, 1):
        response += f"{i}. **{note.get('title', 'Untitled')}**\n"
        response += f"   Similarity: {note.get('similarity_score', 0):.3f}\n"

        # Show snippet of content
        content = note.get('full_content', '')
        if content:
            # Clean content and show first 30 words
            clean_content = content.replace(':PROPERTIES:', '').replace(':END:', '')
            clean_content = ' '.join(clean_content.split()[:30])
            response += f"   Content: {clean_content}...\n"

        # Show connections
        backlinks = note.get('backlinks', [])
        forward_links = note.get('forward_links', [])
        if backlinks or forward_links:
            response += f"   Connections: {len(backlinks)} backlinks, {len(forward_links)} forward links\n"

        response += "\n"

    # Add semantic context summary
    avg_similarity = context.get('average_similarity', 0)
    total_connections = context.get('total_connections', 0)
    embedding_model = context.get('embedding_model', 'unknown')

    response += f"Semantic analysis summary:\n"
    response += f"- Search method: {search_type}\n"
    response += f"- Average similarity: {avg_similarity:.3f}\n"
    response += f"- Total connections: {total_connections}\n"
    response += f"- Embedding model: {embedding_model}\n"

    return response


def _format_basic_search_results(result: Dict[str, Any]) -> str:
    """Format basic search results for display."""
    if not result.get("success"):
        return f"Search failed: {result.get('error', 'Unknown error')}"
    
    notes = result.get("notes", [])
    
    if not notes:
        return "No notes found."
    
    response = f"Found {len(notes)} notes:\n\n"
    
    for i, note in enumerate(notes, 1):
        response += f"{i}. {note.get('title', 'Untitled')}\n"
        response += f"   ID: {note.get('id', 'N/A')}\n"
        response += f"   Created: {note.get('created', 'N/A')}\n"
        if note.get('status'):
            response += f"   Status: {note['status']}\n"
        response += "\n"
    
    return response


async def health_check(request):
    """Health check endpoint."""
    return Response("OK", status_code=200)

def create_starlette_app():
    """Create Starlette app with MCP HTTP endpoint."""
    
    # Create Emacs client instance
    emacs_client = EmacsClient()
    
    async def handle_mcp_request(request: Request):
        """Handle MCP JSON-RPC requests via HTTP POST."""
        try:
            # Parse JSON-RPC request
            body = await request.body()
            rpc_request = json.loads(body)
            
            # Handle different MCP request types
            if rpc_request.get("method") == "tools/list":
                # Return list of available tools
                tools = []
                for tool_name, tool_handler in [
                    ("search_notes", None),
                    ("contextual_search", None),
                    ("get_similar_notes", None),
                    ("create_note", None),
                    ("add_daily_entry", None),
                    ("get_daily_content", None),
                    ("generate_embeddings", None)
                ]:
                    if tool_name == "search_notes":
                        tools.append({
                            "name": "search_notes",
                            "description": "Search org-roam notes by title and content",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "query": {
                                        "type": "string",
                                        "description": "Search query"
                                    }
                                },
                                "required": ["query"]
                            }
                        })
                    elif tool_name == "contextual_search":
                        tools.append({
                            "name": "contextual_search",
                            "description": "Enhanced search with full context for RAG",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "query": {
                                        "type": "string",
                                        "description": "Search query"
                                    },
                                    "limit": {
                                        "type": "integer",
                                        "description": "Maximum number of results",
                                        "default": 10
                                    }
                                },
                                "required": ["query"]
                            }
                        })
                    elif tool_name == "create_note":
                        tools.append({
                            "name": "create_note",
                            "description": "Create a new org-roam note with optional structured formatting for videos",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "title": {
                                        "type": "string",
                                        "description": "Note title"
                                    },
                                    "content": {
                                        "type": "string",
                                        "description": "Note content (transcript for videos)"
                                    },
                                    "type": {
                                        "type": "string",
                                        "description": "Note type (reference, video, concept, etc.)",
                                        "default": "reference"
                                    },
                                    "confidence": {
                                        "type": "string",
                                        "description": "Confidence level",
                                        "default": "medium"
                                    },
                                    "url": {
                                        "type": "string",
                                        "description": "Optional URL (required for video notes)"
                                    },
                                    "metadata": {
                                        "type": "object",
                                        "description": "Optional metadata for structured content",
                                        "properties": {
                                            "duration": {"type": "string"},
                                            "channel": {"type": "string"},
                                            "views": {"type": "string"},
                                            "upload_date": {"type": "string"}
                                        }
                                    }
                                },
                                "required": ["title", "content"]
                            }
                        })
                    elif tool_name == "add_daily_entry":
                        tools.append({
                            "name": "add_daily_entry",
                            "description": "Add structured entry to daily note",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "timestamp": {
                                        "type": "string",
                                        "description": "Entry timestamp (HH:MM format)"
                                    },
                                    "title": {
                                        "type": "string",
                                        "description": "Entry title"
                                    },
                                    "points": {
                                        "type": "array",
                                        "items": {"type": "string"},
                                        "description": "Main points or observations"
                                    },
                                    "next_steps": {
                                        "type": "array",
                                        "items": {"type": "string"},
                                        "description": "Action items or next steps"
                                    },
                                    "tags": {
                                        "type": "array", 
                                        "items": {"type": "string"},
                                        "description": "Tags for the entry"
                                    },
                                    "entry_type": {
                                        "type": "string",
                                        "description": "Entry type: 'journal' (past tense) or 'todo' (future tense)",
                                        "enum": ["journal", "todo"],
                                        "default": "journal"
                                    }
                                },
                                "required": ["timestamp", "title", "points"]
                            }
                        })
                    elif tool_name == "get_daily_content":
                        tools.append({
                            "name": "get_daily_content", 
                            "description": "Get content of daily note",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "date": {
                                        "type": "string",
                                        "description": "Date in YYYY-MM-DD format (optional, defaults to today)"
                                    }
                                },
                                "required": []
                            }
                        })
                    elif tool_name == "generate_embeddings":
                        tools.append({
                            "name": "generate_embeddings",
                            "description": "Generate semantic embeddings for org-roam notes using org-roam-semantic. Processes notes missing embeddings or with stale embeddings.",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "force": {
                                        "type": "boolean",
                                        "description": "Regenerate embeddings even for notes that already have them",
                                        "default": False
                                    }
                                },
                                "required": []
                            }
                        })
                    # Add other tools as needed...
                
                return JSONResponse({
                    "jsonrpc": "2.0",
                    "id": rpc_request.get("id"),
                    "result": {
                        "tools": tools
                    }
                })
            
            elif rpc_request.get("method") == "tools/call":
                # Handle tool execution
                params = rpc_request.get("params", {})
                tool_name = params.get("name")
                arguments = params.get("arguments", {})
                
                if tool_name == "search_notes":
                    result = emacs_client.search_notes(arguments.get("query"))
                    return JSONResponse({
                        "jsonrpc": "2.0",
                        "id": rpc_request.get("id"),
                        "result": {
                            "content": [{"type": "text", "text": str(result)}]
                        }
                    })
                elif tool_name == "contextual_search":
                    result = emacs_client.contextual_search(
                        arguments.get("query"),
                        arguments.get("limit", 10)
                    )
                    return JSONResponse({
                        "jsonrpc": "2.0",
                        "id": rpc_request.get("id"),
                        "result": {
                            "content": [{"type": "text", "text": str(result)}]
                        }
                    })
                elif tool_name == "create_note":
                    title = arguments.get("title")
                    content = arguments.get("content")
                    note_type = arguments.get("type", "reference")
                    confidence = arguments.get("confidence", "medium")
                    url = arguments.get("url")
                    metadata = arguments.get("metadata", {})

                    result = emacs_client.create_note(title, content, note_type, confidence, url, metadata)

                    if result.get("success"):
                        note_info = result.get("note", {})
                        if note_type == "video":
                            response = f"🎥 Video note created successfully: '{title}'\n"
                            response += f"ID: {note_info.get('id', 'N/A')}\n"
                            response += f"File: {note_info.get('file', 'N/A')}\n"
                            if url:
                                response += f"URL: {url}"
                        else:
                            response = f"✅ Note created successfully: '{title}'\n"
                            response += f"ID: {note_info.get('id', 'N/A')}\n"
                            response += f"File: {note_info.get('file', 'N/A')}\n"
                            response += f"Type: {note_type}, Confidence: {confidence}"
                    else:
                        response = f"❌ Failed to create note: {result.get('message', 'Unknown error')}"

                    return JSONResponse({
                        "jsonrpc": "2.0",
                        "id": rpc_request.get("id"),
                        "result": {
                            "content": [{"type": "text", "text": response}]
                        }
                    })
                elif tool_name == "add_daily_entry":
                    timestamp = arguments.get("timestamp")
                    title = arguments.get("title")
                    points = arguments.get("points", [])
                    next_steps = arguments.get("next_steps", [])
                    tags = arguments.get("tags", [])
                    entry_type = arguments.get("entry_type", "journal")
                    
                    result = emacs_client.add_daily_entry(
                        timestamp, title, points, next_steps, tags, entry_type
                    )
                    
                    if result.get("success"):
                        # Return the exact response format the agent expects to see
                        entry_label = "TODO" if entry_type == "todo" else "JOURNAL"
                        response = f"✅ **{entry_label}** added to daily note: {title}"
                    else:
                        response = f"❌ Failed to add daily entry: {result.get('error', 'Unknown error')}"
                    
                    return JSONResponse({
                        "jsonrpc": "2.0",
                        "id": rpc_request.get("id"),
                        "result": {
                            "content": [{"type": "text", "text": response}]
                        }
                    })
                elif tool_name == "get_daily_content":
                    result = emacs_client.get_daily_content(
                        arguments.get("date")
                    )
                    return JSONResponse({
                        "jsonrpc": "2.0",
                        "id": rpc_request.get("id"),
                        "result": {
                            "content": [{"type": "text", "text": str(result)}]
                        }
                    })
                elif tool_name == "generate_embeddings":
                    force = arguments.get("force", False)
                    result = emacs_client.generate_embeddings(force)

                    if result.get("success"):
                        count = result.get("count", 0)
                        response = f"✅ Generated {count} embeddings for org-roam notes"
                        if count == 0:
                            response += "\n(All notes already have current embeddings)"
                    else:
                        response = f"❌ Failed to generate embeddings: {result.get('error', 'Unknown error')}"

                    return JSONResponse({
                        "jsonrpc": "2.0",
                        "id": rpc_request.get("id"),
                        "result": {
                            "content": [{"type": "text", "text": response}]
                        }
                    })
                # Add other tool handlers as needed...
                
                return JSONResponse({
                    "jsonrpc": "2.0",
                    "id": rpc_request.get("id"),
                    "error": {
                        "code": -32601,
                        "message": f"Unknown tool: {tool_name}"
                    }
                })
            
            # Handle initialize request
            elif rpc_request.get("method") == "initialize":
                return JSONResponse({
                    "jsonrpc": "2.0",
                    "id": rpc_request.get("id"),
                    "result": {
                        "protocolVersion": "2025-03-26",
                        "capabilities": {
                            "tools": {}
                        },
                        "serverInfo": {
                            "name": "org-roam-mcp",
                            "version": "0.1.0"
                        }
                    }
                })
            
            else:
                return JSONResponse({
                    "jsonrpc": "2.0",
                    "id": rpc_request.get("id"),
                    "error": {
                        "code": -32601,
                        "message": f"Unknown method: {rpc_request.get('method')}"
                    }
                })
        
        except Exception as e:
            logger.error(f"Error handling MCP request: {e}")
            return JSONResponse({
                "jsonrpc": "2.0",
                "id": rpc_request.get("id") if "rpc_request" in locals() else None,
                "error": {
                    "code": -32603,
                    "message": f"Internal error: {str(e)}"
                }
            }, status_code=500)
    
    starlette_app = Starlette(
        routes=[
            Route("/", health_check, methods=["GET"]),
            Route("/", handle_mcp_request, methods=["POST"]),
        ]
    )
    
    # Add CORS middleware
    starlette_app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    
    return starlette_app

async def main():
    """Main async entry point for the MCP server."""
    import uvicorn
    logger.info("Starting org-roam MCP HTTP server on port 8000...")
    logger.info("Available endpoints:")
    logger.info("  GET  / - Health check")
    logger.info("  POST / - MCP JSON-RPC endpoint for n8n")

    starlette_app = create_starlette_app()

    config = uvicorn.Config(
        starlette_app,
        host="0.0.0.0",
        port=8000,
        log_level="info"
    )
    server = uvicorn.Server(config)
    await server.serve()


def cli_main():
    """Synchronous entry point for console script."""
    asyncio.run(main())


if __name__ == "__main__":
    cli_main()