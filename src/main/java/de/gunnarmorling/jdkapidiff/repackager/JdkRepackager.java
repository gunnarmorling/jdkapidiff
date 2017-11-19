/**
 *  Copyright 2017 Gunnar Morling (http://www.gunnarmorling.de/)
 *  and/or other contributors as indicated by the @authors tag. See the
 *  copyright.txt file in the distribution for a full listing of all
 *  contributors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.gunnarmorling.jdkapidiff.repackager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.spi.ToolProvider;

import de.gunnarmorling.jdkapidiff.ProcessExecutor;

public abstract class JdkRepackager {

    protected final Path javaHome;
    protected final String version;

    public static JdkRepackager getJdkRepackager(Path javaHome) {
        String version = getVersion( javaHome );

        if ( version.startsWith( "1.") ) {
            return new Jdk8Repackager( javaHome, version );
        }
        else {
            return new Jdk9Repackager( javaHome, version );
        }
    }

    protected JdkRepackager(Path javaHome, String version) {
        this.javaHome = javaHome;
        this.version = version;
    }

    public void mergeJavaApi(Path workingDir, Path extractedClassesDir, List<String> excludes) throws IOException {
        System.out.println( "Merging JARs/modules from " + javaHome + " (version " + version + ")" );

        Path targetDir = extractedClassesDir.resolve( version );
        Files.createDirectories( targetDir );

        extractJdkClasses( targetDir );

        Path fileList = Paths.get( workingDir.toUri() ).resolve( version + "-files" );

        Files.write(
                fileList,
                getFileList( targetDir, excludes ).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE
        );

        String apiJarName = "java-" + version + "-api.jar";
        System.out.println( "Creating " + apiJarName );

        Optional<ToolProvider> jar = ToolProvider.findFirst( "jar" );
        if ( !jar.isPresent() ) {
            throw new IllegalStateException( "Couldn't find jar tool" );
        }

        jar.get().run(
                System.out,
                System.err,
                "-cf", extractedClassesDir.getParent().resolve( apiJarName ).toString(),
                "@" + fileList
        );
    }

    protected abstract void extractJdkClasses(Path targetDir) throws IOException;

    private static String getVersion(Path javaHome) {
        List<String> output = ProcessExecutor.run( "java", Arrays.asList( javaHome.resolve( "bin" ).resolve( "java" ).toString(), "-version" ), javaHome.resolve( "bin" ) );
        String version = output.get( 0 );
        return version.substring( version.indexOf( "\"") + 1, version.length() - 1);
    }

    private static String getFileList(Path java8Dir, List<String> excludes) {
        StringBuilder fileList = new StringBuilder();

        try {
            Files.walkFileTree( java8Dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    java8Dir.relativize( file );

                    for ( String exclude : excludes ) {
                        if ( file.startsWith( exclude ) ) {
                            return FileVisitResult.CONTINUE;
                        }
                    }

                    fileList.append( file ).append( System.lineSeparator() );

                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e) {
            throw new RuntimeException( e );
        }

        return fileList.toString();
    }
}