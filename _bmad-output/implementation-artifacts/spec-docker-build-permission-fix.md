---
title: 'Fix Docker Build Permission Denied Error'
type: 'bugfix'
created: '2026-04-30'
status: 'draft'
context: ['_bmad-output/project-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Docker Compose build fails with "permission denied" error when trying to copy build context containing root-owned temporary files in the `tmp/` directory. This prevents the development environment from starting and blocks all development work.

**Approach:** Clean up root-owned temporary files, create proper `.dockerignore` to exclude temporary directories from build context, and remove the problematic host tmp volume mount that causes permission conflicts.

## Boundaries & Constraints

**Always:**
- Preserve all application functionality and existing Docker configuration that works
- Keep Spring Boot temporary file isolation between host and container
- Maintain clean separation between development and runtime temporary files
- Ensure Docker build works for all team members regardless of their host OS permissions

**Ask First:**
- Changing user management in Dockerfiles (UID/GID mapping)
- Modifying volume mount strategies for other directories beyond tmp
- Any changes to Docker Compose services beyond the app container

**Never:**
- Remove the entire `tmp/` directory permanently (Spring Boot needs it)
- Change Spring Boot's temporary file configuration
- Modify any non-Docker related application code or configuration

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Clean build | Fresh repo clone | Docker build succeeds | N/A |
| Root-owned tmp files | Previous container runs | Build succeeds after cleanup | Auto-cleanup via .dockerignore |
| Mixed permissions | Some host, some container files | Build ignores tmp entirely | Files remain on host, excluded from context |

</frozen-after-approval>

## Code Map

- `.dockerignore` -- CREATE: exclude temporary directories from Docker build context
- `docker-compose.yml` -- MODIFY: remove problematic tmp volume mount
- `tmp/` -- CLEANUP: remove root-owned files blocking build context

## Tasks & Acceptance

**Execution:**
- [ ] `tmp/` -- Clean up root-owned files preventing Docker access -- resolves immediate permission error
- [ ] `.dockerignore` -- Create file to exclude tmp/, logs/, target/, .git/ from build context -- prevents future permission issues
- [ ] `docker-compose.yml` -- Remove `./tmp:/tmp` volume mount from app service -- eliminates host/container permission conflicts

**Acceptance Criteria:**
- Given a repository with root-owned files in tmp/, when running `docker compose up -d --build`, then the build completes successfully without permission errors
- Given the new .dockerignore file, when Docker builds the context, then temporary directories are excluded from the build process
- Given the updated docker-compose.yml, when containers run, then they use container-internal tmp directories without mounting host tmp

## Verification

**Commands:**
- `docker compose down` -- expected: all containers stopped cleanly
- `docker compose up -d --build` -- expected: successful build and container startup
- `docker compose logs app` -- expected: no permission errors in application logs

**Manual checks:**
- Verify .dockerignore contains tmp/, logs/, target/, .git/ entries
- Confirm docker-compose.yml no longer has `./tmp:/tmp` volume mount
- Check that tmp/ directory exists but Docker build ignores it