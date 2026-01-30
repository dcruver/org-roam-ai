# Second Brain Enhancement Plan

This plan documents enhancements to org-roam-ai inspired by engineering principles for building reliable personal knowledge systems. The goal is to add trust mechanisms, proactive surfacing, and structured routing while leveraging org-roam's existing strengths.

**Reference**: [Building a Second Brain with AI (2026)](https://www.youtube.com/watch?v=0TpON5T-Sw4)

---

## Implementation Status

**Last Updated**: 2026-01-14

### Phase 1: Command Router & Inbox ✅ COMPLETE

| Task | Status | Notes |
|------|--------|-------|
| Replace Intent Classifier with Command Router | ✅ Done | Deterministic routing, no LLM needed |
| Update Intent Switch for command-based routing | ✅ Done | Routes: journal, note, yt, search, help |
| Add unknown command handler | ✅ Done | Returns help message |
| Add `my/api-add-inbox-entry` elisp function | ✅ Done | In org-roam-api.el |
| Add `add_inbox_entry` MCP tool | ✅ Done | In server.py |
| Deploy MCP to n8n-backend | ✅ Done | v0.1.16 |
| Add Log to Inbox node in n8n | ✅ Done | Parallel branch from Command Router |
| Remove redundant Org-Roam Webhook | ✅ Done | Single webhook now |
| Fix bugs (JSON escaping, IPv6, Help syntax) | ✅ Done | All resolved |

**Verified Working**:
- `/help` - Shows available commands
- `/journal <text>` - Adds to daily note
- `/note <topic>` - Creates notes
- `/yt <url>` - Captures YouTube videos
- All commands logged to daily note Inbox section

### Phase 2: Structured Node Types ✅ COMPLETE

| Task | Status | Notes |
|------|--------|-------|
| `/person` command handler | ✅ Done | Creates person nodes in people/ directory |
| `/project` command handler | ✅ Done | Creates project nodes in projects/ directory |
| `/idea` command handler | ✅ Done | Creates idea nodes in ideas/ directory |
| `/admin` command handler | ✅ Done | Creates admin tasks in admin/ directory |
| LLM field extraction for each type | ✅ Done | vLLM extracts structured fields from natural language |
| Elisp functions | ✅ Done | my/api-create-{person,project,idea,admin} |
| MCP tools | ✅ Done | create_{person,project,idea,admin} in v0.2.0 |
| n8n workflow handlers | ✅ Done | Field Extractor + MCP Call nodes for each |

**Verified Working**:
- `/person sarah works at acme corp on the ml team` - Creates person note with context
- `/project website relaunch - waiting on copy` - Creates project with status
- `/idea use token bucket for rate limiting` - Creates idea with one-liner
- `/admin renew domain by jan 20` - Creates admin task with due date
- All commands logged to daily note Inbox section

### Phase 3: Proactive Surfacing 🔄 IN PROGRESS

| Task | Status | Notes |
|------|--------|-------|
| Elisp query functions | ✅ Done | my/api-get-{active-projects,pending-followups,stale-projects,weekly-inbox,digest-data} |
| MCP tools | ✅ Done | get_{active_projects,pending_followups,stale_projects,weekly_inbox,digest_data} in v0.3.0 |
| `/digest` command routing | ✅ Done | Added to Command Router and Intent Switch |
| `/digest` handler node | ✅ Done | Digest MCP Call node added |
| Test /digest via Matrix | ✅ Done | Working |
| Daily digest scheduled workflow | ✅ Done | Morning Matrix DM |
| Weekly review scheduled workflow | ⏳ Planned | Sunday Matrix DM |

### Known Issues

| Issue | Status |
|-------|--------|
| `/search` command not returning results | Deferred - user will rely on Emacs for search |

---

## Current State

### What We Have
- **Capture**: Matrix chatbot for frictionless input
- **Routing**: Command Router with explicit `/commands` (Phase 1 complete)
- **Storage**: org-roam files with properties, links, embeddings
- **Retrieval**: Semantic search via MCP
- **Compute**: n8n workflows + vLLM for processing
- **Health Tracking**: Explicit commands (`/glucose`, `/ketone`, etc.)

### What's Missing (Next Phases)
- ~~**Audit Trail**: No log of what was captured and where it went~~ ✅ Done (Phase 1)
- **Proactive Surfacing**: No daily/weekly digests pushed to user (Phase 3)
- ~~**Structured Node Types**: No People/Projects schema with actionable fields~~ ✅ Done (Phase 2)
- ~~**Explicit Commands**: Currently using Intent Classifier instead of commands~~ ✅ Done (Phase 1)

---

## Key Design Decision: Explicit Commands Over Intent Classification

The original video recommends AI classification for non-coders who don't know what bucket something belongs in. For an engineer, explicit commands are better:

| Intent Classifier | Explicit Commands |
|-------------------|-------------------|
| Frictionless but can misclassify | User declares intent, zero ambiguity |
| Requires confidence handling | No confidence scores needed |
| Extra LLM call per message | Faster, skip classification step |
| "Magic" that sometimes fails | Predictable, does what you asked |

**Decision**: Replace Intent Classifier with explicit commands, matching the existing `/glucose`, `/ketone` pattern.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  Matrix (Capture Interface)                                     │
│  - Explicit commands: /journal, /person, /project, etc.         │
│  - Health commands: /glucose, /ketone, /weight, etc.            │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│  Command Router (n8n Switch node)                               │
│  - Parse command prefix                                         │
│  - Route to appropriate handler                                 │
│  - No LLM needed for routing                                    │
└─────────────────────┬───────────────────────────────────────────┘
                      │
    ┌─────────────────┼─────────────────┐
    ▼                 ▼                 ▼
┌────────┐      ┌──────────┐      ┌──────────┐
│/journal│      │ /person  │      │ /search  │
│/note   │      │ /project │      │ /yt      │
│/idea   │      │ /admin   │      │ etc.     │
└────┬───┘      └────┬─────┘      └────┬─────┘
     │               │                 │
     └───────────────┼─────────────────┘
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│  Daily Note Inbox Section (Audit Trail)                         │
│  - Log all captures with command used                           │
│  - Link to created notes                                        │
│  - Timestamp and original text                                  │
└─────────────────────────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│  org-roam Files (Memory Store)                                  │
│  - people/*.org     (NODE-TYPE: person)                         │
│  - projects/*.org   (NODE-TYPE: project)                        │
│  - ideas/*.org      (NODE-TYPE: idea)                           │
│  - admin/*.org      (NODE-TYPE: admin)                          │
│  - daily/*.org      (daily notes + inbox)                       │
└─────────────────────────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│  Proactive Surfacing                                            │
│  - Daily Digest: Morning Matrix DM with top actions             │
│  - Weekly Review: Sunday summary of themes and stuck items      │
└─────────────────────────────────────────────────────────────────┘
```

---

## Command Reference

### Capture Commands

| Command | Action | Example |
|---------|--------|---------|
| `/journal` | Add entry to daily note | `/journal finished the API refactor` |
| `/note` | Create general note | `/note kubernetes network policies` |
| `/person` | Create/update person node | `/person sarah mentioned moving to denver` |
| `/project` | Create/update project node | `/project website relaunch - waiting on copy` |
| `/idea` | Create idea node | `/idea use token bucket for rate limiting` |
| `/admin` | Create admin task | `/admin renew domain by jan 20` |

### Action Commands

| Command | Action | Example |
|---------|--------|---------|
| `/search` | Semantic search | `/search kubernetes networking` |
| `/yt` | Capture YouTube video | `/yt https://youtube.com/watch?v=...` |

### Health Commands (existing)

| Command | Action |
|---------|--------|
| `/glucose` | Log glucose reading |
| `/ketone` | Log ketone reading |
| `/weight` | Log weight |
| `/meal` | Log meal |
| `/bp` | Log blood pressure |

### Utility Commands

| Command | Action | Example |
|---------|--------|---------|
| `/help` | List available commands | `/help` |
| `/digest` | Get today's digest on demand | `/digest` |

---

## Phase 1: Command Router & Inbox

**Goal**: Replace Intent Classifier with explicit commands. Log all captures to daily note.

### 1.1 Command Router

Replace the Intent Classifier chain with a simple Switch node that parses command prefixes:

```javascript
// Extract command from message
const message = $json.body.message;
const match = message.match(/^\/(\w+)\s*(.*)/);
if (match) {
  return { command: match[1], content: match[2] };
} else {
  return { command: 'unknown', content: message };
}
```

### 1.2 Daily Note Inbox Section

Log all captures to the daily note:

```org
* Inbox
** DONE /person → [[id:abc123][Sarah Chen]]
   :PROPERTIES:
   :CAPTURED: [2026-01-12 Sun 14:32]
   :COMMAND: person
   :ORIGINAL: /person sarah mentioned moving to denver
   :END:
** DONE /journal → daily entry added
   :PROPERTIES:
   :CAPTURED: [2026-01-12 Sun 15:10]
   :COMMAND: journal
   :ORIGINAL: /journal finished the API refactor
   :END:
```

### 1.3 Unknown Command Handling

If command is not recognized:

```
❓ Unknown command. Available commands:
/journal - Add to daily note
/note - Create a note
/person - Log person info
/project - Log project update
/idea - Capture an idea
/admin - Create a task
/search - Search your notes
/yt - Capture YouTube video
/help - Show this help
```

### 1.4 Implementation Tasks

| Component | Task | Details |
|-----------|------|---------|
| n8n workflow | Remove Intent Classifier | Delete the chain node and vLLM connection |
| n8n workflow | Add Command Parser | Code node to extract command and content |
| n8n workflow | Update Switch node | Route based on command instead of intent |
| org-roam-api.el | New function | `org-roam-api-add-inbox-entry` |
| MCP server | New tool | `add_inbox_entry` |
| n8n workflow | Add inbox logging | Call `add_inbox_entry` for all commands |

### 1.5 Inbox Entry Schema

```json
{
  "command": "person|project|idea|admin|journal|note|search|yt",
  "original_text": "full message including command",
  "content": "message without command prefix",
  "linked_note_id": "optional - org-roam ID if note was created",
  "linked_note_title": "optional - title of created note"
}
```

---

## Phase 2: Structured Node Types

**Goal**: Add People and Projects as first-class node types with actionable fields.

### 2.1 Node Type Schemas

**Person** (`NODE-TYPE: person`)
```org
:PROPERTIES:
:ID: uuid
:NODE-TYPE: person
:CONTEXT: How you know them, their role
:LAST-CONTACT: [2026-01-10 Fri]
:END:
#+title: Sarah Chen

* Follow-ups
- [ ] Ask about Denver move
- [ ] Send article about ML ops

* Notes
- Works at Acme Corp, ML team lead
- Met at KubeCon 2025
```

**Project** (`NODE-TYPE: project`)
```org
:PROPERTIES:
:ID: uuid
:NODE-TYPE: project
:STATUS: active|waiting|blocked|someday|done
:NEXT-ACTION: Email Sarah to confirm copy deadline
:END:
#+title: Website Relaunch

* Next Actions
- [ ] Email Sarah to confirm copy deadline
- [ ] Review mockups from designer

* Notes
- Target launch: Q1 2026
- Blocked on final copy approval
```

**Idea** (`NODE-TYPE: idea`)
```org
:PROPERTIES:
:ID: uuid
:NODE-TYPE: idea
:ONE-LINER: Brief summary of the insight
:END:
#+title: API Rate Limiting Pattern

* One-liner
Use token bucket algorithm with Redis for distributed rate limiting.

* Elaboration
...
```

**Admin** (`NODE-TYPE: admin`)
```org
:PROPERTIES:
:ID: uuid
:NODE-TYPE: admin
:DUE-DATE: [2026-01-15 Wed]
:STATUS: todo|done
:END:
#+title: Renew domain registration

* Notes
- cruver.network expires Jan 20
- Use Cloudflare registrar
```

### 2.2 LLM-Assisted Field Extraction

Even though routing is explicit, use LLM to extract structured fields from the content:

```
/person sarah mentioned moving to denver, works at acme on ml team
```

LLM extracts:
```json
{
  "name": "Sarah",
  "context": "Works at Acme on ML team",
  "follow_ups": ["Ask about Denver move"],
  "notes": "Mentioned moving to Denver"
}
```

This keeps capture frictionless while still populating structured fields.

### 2.3 Implementation Tasks

| Component | Task | Details |
|-----------|------|---------|
| org-roam-api.el | New function | `org-roam-api-create-person` with schema |
| org-roam-api.el | New function | `org-roam-api-create-project` with schema |
| org-roam-api.el | New function | `org-roam-api-create-idea` with schema |
| org-roam-api.el | New function | `org-roam-api-create-admin` with schema |
| MCP server | New tools | `create_person`, `create_project`, `create_idea`, `create_admin` |
| n8n workflow | Add extraction chains | LLM chains to extract fields per node type |
| n8n workflow | Update command handlers | Call appropriate create function |

### 2.4 Field Extraction Prompts

**Person extraction prompt**:
```
Extract person information from this message. Return JSON only.

Message: {content}

Schema:
{
  "name": "Person's name",
  "context": "How user knows them, their role (optional)",
  "follow_ups": ["Action items related to this person"],
  "notes": "Any other details mentioned"
}
```

**Project extraction prompt**:
```
Extract project information from this message. Return JSON only.

Message: {content}

Schema:
{
  "title": "Project name",
  "status": "active|waiting|blocked|someday",
  "next_action": "Specific next step (must be actionable)",
  "notes": "Any other details mentioned"
}
```

---

## Phase 3: Proactive Surfacing

**Goal**: Push relevant information to you without searching.

### 3.1 Daily Digest

**Trigger**: Scheduled (e.g., 7:00 AM) or on-demand via `/digest`

**Content** (< 150 words):
```
Good morning! Here's your focus for today:

📋 Top 3 Actions:
1. Email Sarah to confirm copy deadline (Website Relaunch)
2. Review PR #234 (org-roam-ai)
3. Call dentist to reschedule

👥 Follow-ups:
- Sarah Chen: Ask about Denver move
- Mike R: Send Kubernetes article

⚠️ Might be stuck:
- API Integration project (no activity in 5 days)
```

**Data Sources**:
- Projects with status=active, ordered by last touched
- People with pending follow-ups
- Projects with no recent activity

### 3.2 Weekly Review

**Trigger**: Scheduled (e.g., Sunday 4:00 PM)

**Content** (< 250 words):
```
Weekly Review: Jan 6-12, 2026

📊 This Week:
- 12 items captured
- 3 projects moved forward
- 2 new people added

✅ Completed:
- Website Relaunch: Copy approved
- Fix MCP truncation bug

🔄 Open Loops:
- API Integration: Waiting on vendor response
- Tax documents: Due Jan 15

🎯 Suggested Focus Next Week:
1. Follow up with vendor on API
2. Complete tax document prep
3. Schedule 1:1 with Sarah

💡 Theme I noticed:
You've captured 4 items about "performance optimization" - consider creating a project for this.
```

**Data Sources**:
- Inbox entries from past 7 days
- Projects by status
- Semantic clustering of recent captures

### 3.3 Implementation Tasks

| Component | Task | Details |
|-----------|------|---------|
| org-roam-api.el | New function | `org-roam-api-get-active-projects` |
| org-roam-api.el | New function | `org-roam-api-get-pending-followups` |
| org-roam-api.el | New function | `org-roam-api-get-weekly-inbox` |
| org-roam-api.el | New function | `org-roam-api-get-stale-projects` |
| MCP server | New tools | Wrap the above elisp functions |
| n8n workflow | New workflow | Daily digest: query → summarize → Matrix DM |
| n8n workflow | New workflow | Weekly review: query → analyze → Matrix DM |
| n8n workflow | Add /digest handler | On-demand digest command |

---

## Phase 4: Refinements

**Goal**: Polish and optimize based on usage.

### 4.1 Update Existing Notes

For `/person` and `/project`, check if note already exists and append:

```
/person sarah - confirmed she's moving in march
```

If "Sarah Chen" note exists:
- Add to Notes section
- Update LAST-CONTACT property
- Don't create duplicate

### 4.2 Semantic Enhancements

- **Auto-linking**: When creating a note, find and suggest links to related notes
- **Duplicate detection**: Before creating, check if similar note exists

### 4.3 Voice Capture (Optional)

- Add voice message handling to Matrix bot
- Transcribe via Whisper
- Process same as text with detected command

---

## Implementation Order

| Phase | Effort | Impact | Dependencies |
|-------|--------|--------|--------------|
| 1. Command Router & Inbox | Medium | High | None |
| 2. Structured Node Types | Medium | Medium | Phase 1 |
| 3. Proactive Surfacing | High | Very High | Phases 1-2 |
| 4. Refinements | Varies | Medium | Phases 1-3 |

**Recommended Start**: Phase 1 - simplifies the system while adding visibility.

---

## Design Principles

1. **Explicit over implicit**: Commands declare intent, no guessing
2. **Separate memory/compute/interface**: org-roam / MCP+n8n / Matrix
3. **LLM for extraction, not routing**: Use LLM to parse fields, not to decide where things go
4. **Build trust through visibility**: Inbox log shows everything that happened
5. **Small, frequent outputs**: Digests under 150/250 words
6. **Next action as unit**: Projects must have actionable next steps
7. **Few categories/fields**: person/project/idea/admin only
8. **Design for restart**: No backlog guilt, just resume
9. **Core loop + modules**: Capture→route→file→surface, then add features
10. **Maintainability > cleverness**: Fewer moving parts, clear logs

---

## Files to Modify

| File | Changes |
|------|---------|
| `packages/org-roam-ai/org-roam-api.el` | New functions for inbox, node types, queries |
| `mcp/src/org_roam_mcp/server.py` | New MCP tools wrapping elisp functions |
| `n8n workflow` | Remove Intent Classifier, add command router, add digest workflows |

---

## Migration Steps

### Remove Intent Classifier

1. Delete Intent Classifier chain node
2. Delete vLLM Chat Model connection to it
3. Update Switch node to check `command` instead of `intent`

### Add Command Router

1. Add Code node after Message Extractor:
```javascript
const message = $json.body.message.trim();
const match = message.match(/^\/(\w+)\s*(.*)/s);
if (match) {
  return {
    json: {
      command: match[1].toLowerCase(),
      content: match[2].trim(),
      originalMessage: message
    }
  };
} else {
  return {
    json: {
      command: 'unknown',
      content: message,
      originalMessage: message
    }
  };
}
```

2. Update Switch node conditions:
   - `{{ $json.command }}` equals `journal` → Journal flow
   - `{{ $json.command }}` equals `person` → Person flow
   - `{{ $json.command }}` equals `project` → Project flow
   - etc.

---

## Success Metrics

- [x] All captures logged in daily note inbox ✅ (2026-01-12)
- [x] Command response time < 3 seconds (no classification delay) ✅ (2026-01-12)
- [x] Unknown commands get helpful error message ✅ (2026-01-12)
- [x] Person/project notes have structured fields populated ✅ (2026-01-13)
- [x] MCP tools for digest queries working ✅ (2026-01-13)
- [x] All 319 notes classified by NODE-TYPE ✅ (2026-01-13)
- [x] /digest command via Matrix ✅ (2026-01-14)
- [x] Daily digest delivered by 7:30 AM ✅ (2026-01-14)
- [ ] Weekly review delivered Sunday evening (Phase 3)
- [ ] System restartable after any gap without cleanup
