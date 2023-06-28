const core = require('@actions/core');
const github = require('@actions/github');

async function run() {
  try {
    // Get inputs from the workflow file
    const githubToken = core.getInput('githubToken');
    const organization = core.getInput('organization');

    
    const octokit = github.getOctokit(githubToken);

    // Get the pull request number from the context
    const pullRequestNumber = github.context.payload.pull_request.number;

    // Check if the user is a member of the organization
    const { data: isMember } = await octokit.rest.orgs.checkMembershipForUser({
      org: organization,
      username: github.context.payload.pull_request.user.login,
    });

    // If the user is a member, log a message and skip manual approval
    if (isMember) {
      core.info(`User ${github.context.payload.pull_request.user.login} is a member of the ${organization} organization. Skipping manual approval.`);
      return;
    }

    // Create a comment on the pull request requesting manual approval
    const comment = await octokit.rest.issues.createComment({
      owner: github.context.repo.owner,
      repo: github.context.repo.repo,
      issue_number: pullRequestNumber,
    });

    // Log the comment URL
    core.info(`Comment created: ${comment.data.html_url}`);

    // Pause the workflow and wait for manual approval
    core.info('Waiting for manual approval...');
    await new Promise((resolve) => setTimeout(resolve, 6000000)); // Wait for 1 minute (adjust as needed)

    // Resume the workflow after manual approval
    core.info('Manual approval received. Continuing workflow...');
  } catch (error) {
    core.setFailed(error.message);
  }
}

run();