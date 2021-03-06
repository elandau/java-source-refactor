package com.netflix.java.refactor.gradle

import com.netflix.java.refactor.AutoRefactor
import com.netflix.java.refactor.SourceSet
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.impldep.com.google.common.collect.HashMultimap
import org.gradle.logging.StyledTextOutputFactory
import java.io.File
import javax.inject.Inject

open class RefactorAndFixSourceTask : DefaultTask() {
    // see http://gradle.1045684.n5.nabble.com/injecting-dependencies-into-task-instances-td5712637.html
    @Inject
    open fun getTextOutputFactory(): StyledTextOutputFactory? = null
    
    private class RuleDescriptor(val name: String, val description: String)
    
    @TaskAction
    fun refactorSource() {
        val fixesByRule = HashMultimap.create<RuleDescriptor, File>()

        project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets.forEach {
            val sourceSet = SourceSet(it.allJava, it.compileClasspath)
            sourceSet.allAutoRefactorsOnClasspath().forEach {
                val (refactor, scanner) = it
                sourceSet.allJava().forEach { source ->
                    scanner.scan(source)
                    if(source.changedFile) {
                        fixesByRule.put(RuleDescriptor(refactor.value, refactor.description), source.file())
                    }
                }
            }
        }
        
        printReport(fixesByRule.asMap())
    }
    
    private fun printReport(fixesByRule: Map<RuleDescriptor, Collection<File>>) {
        val textOutput = getTextOutputFactory()!!.create(RefactorAndFixSourceTask::class.java)
        
        if(fixesByRule.isEmpty()) {
            textOutput.style(Styling.Green).println("Passed refactoring check with no changes necessary")
        } else {
            textOutput.text("Refactoring operations were performed on this project. ")
                    .withStyle(Styling.Bold).println("Please review the changes and commit.\n")
            
            fixesByRule.entries.forEachIndexed { i, entry ->
                val (rule, ruleFixes) = entry
                textOutput.withStyle(Styling.Bold).text("${"${i+1}.".padEnd(2)} ${rule.description}")
                textOutput.text(" (${ruleFixes.size} files changed) - ")
                textOutput.withStyle(Styling.Yellow).println(rule.description)
            }
        }
    }
}