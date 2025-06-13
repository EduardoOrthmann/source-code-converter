package tsystems.janus.sourcecodeconverter.infrastructure.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;

@Component
public class GitCloner {

    public File cloneRepository(String repoUrl) throws Exception {
        File tempDir = Files.createTempDirectory("cloned-repo").toFile();

        try {
            Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(tempDir)
                    .call();
        } catch (GitAPIException e) {
            throw new RuntimeException("Error cloning repository: " + repoUrl, e);
        }

        return tempDir;
    }
}
