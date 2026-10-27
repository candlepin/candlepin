import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

import java.io.File

import static org.junit.jupiter.api.Assertions.assertTrue

class PomGeneratorTest {

    @Test
    void declaresMavenLockfileExtensionAsGeneratedOutput() {
        Project project = ProjectBuilder.builder().build()
        project.pluginManager.apply(PomGenerator)

        PomGeneratorTask task = project.tasks.named("generatePom", PomGeneratorTask).get()
        File extensionFile = new File(project.projectDir, "maven/war/.mvn/extensions.xml")

        assertTrue(task.outputPomFiles.contains(extensionFile))
    }

}
