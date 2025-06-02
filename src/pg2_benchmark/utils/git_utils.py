from loguru import logger
import git

def git_clone(repo_url: str, target_dir: str, branch_name: str) -> None:
    logger.info(f"Cloning repository from {repo_url} to {target_dir}...")
    
    try:
        repo = git.Repo.clone_from(url=repo_url, to_path=target_dir, branch=branch_name)
        logger.info(f"Repository successfully cloned to {target_dir}")
    
    except git.GitCommandError as exc:
        logger.error(f"Error cloning repository: {exc}")
        return
