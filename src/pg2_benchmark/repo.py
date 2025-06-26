import git
import logging

logger = logging.getLogger(__name__)

def clone(repo_url: str, target_dir: str, branch_name: str) -> None:
    logger.info(f"Cloning repository from {repo_url} to {target_dir}...")
    
    try:
        git.Repo.clone_from(url=repo_url, to_path=target_dir, branch=branch_name)
        logger.info(f"Repository successfully cloned to {target_dir}")
    
    except git.GitCommandError as e:
        logger.error(f"Error cloning repository: {e}", exc_info=True)
        return