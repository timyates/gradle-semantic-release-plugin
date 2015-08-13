/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.gliderpilot.gradle.semanticrelease

import nebula.test.IntegrationSpec
import spock.lang.Requires
import spock.lang.Unroll

/**
 * Created by tobias on 7/2/15.
 */
// always run on travis
// also run on ./gradlew integTest
@Requires({ env['TRAVIS'] || properties['integTest'] })
class SemanticReleasePluginIntegrationSpec extends IntegrationSpec {

    def setup() {
        // create remote repository
        File origin = new File(projectDir, "../${projectDir.name}.git")
        origin.mkdir()
        execute origin, 'git', 'init', '--bare'

        // create workspace
        execute 'git', 'init'
        execute 'git', 'config', '--local', 'user.name', "Me"
        execute 'git', 'config', '--local', 'user.email', "me@example.com"


        execute 'git', 'remote', 'add', 'origin', "$origin"
        commit 'initial commit'
        execute 'git', 'push', 'origin', 'HEAD', '-u'

        buildFile << '''
            apply plugin: 'de.gliderpilot.semantic-release'
            println version
        '''
        file('.gitignore') << '''\
            .gradle-test-kit/
            .gradle/
            gradle/
            build/
        '''.stripIndent()

        runTasksSuccessfully(':wrapper')

        commit('initial project layout')
        push()
    }

    @Unroll
    def "initial version is 1.0.0 after #type commit"() {
        when: "a commit with type $type is made"
        commit("$type: foo")

        then:
        release() == 'v1.0.0'

        where:
        type << ['feat', 'fix']
    }

    def "initial version is 1.0.0 even after breaking change"() {
        when: "a breaking change commit is made without prior version"
        commit("feat: foo\n\nBREAKING CHANGE: bar")

        then:
        release() == 'v1.0.0'
    }

    def "complete lifecycle"() {
        expect: 'no initial release without feature commit'
        release() == ''

        when: 'initial version is 1.0.0 after feature commit'
        commit("feat: foo")

        then:
        release() == 'v1.0.0'

        and: 'no release, if no changes'
        release() == 'v1.0.0'

        when: 'unpushed but committed fix'
        commit('fix: some commit message')

        then: 'release is performed'
        release() == 'v1.0.1'

        when: 'feature commit'
        commit('feat: feature')
        push()

        then: 'new minor release'
        release() == 'v1.1.0'

        when: 'feature commit but dirty workspace'
        commit('feat: feature')
        file('README.md') << '.'

        then: 'no release'
        release().startsWith('v1.1.0')

        when: 'breaking change'
        commit '''\
            feat: Feature

            BREAKING CHANGE: everything changed
        '''.stripIndent()

        then: 'major release'
        release() == 'v2.0.0'

        when: 'empty commit message'
        commit('')
        push()

        then: 'no new version'
        release().startsWith('v2.0.0')

    }

    def execute(File dir = projectDir, String... args) {
        println "========"
        println "executing ${args.join(' ')}"
        println "--------"
        def process = args.execute(null, dir)
        String processOut = process.inputStream.text.trim()
        String processErr = process.errorStream.text.trim()
        println processOut
        println processErr
        if (process.waitFor() != 0)
            throw new RuntimeException("failed to execute ${args.join(' ')}")
        return processOut
    }

    def release() {
        execute './gradlew', '-I', '.gradle-test-kit/init.gradle', 'release', '--info', '--stacktrace'
        try {
            execute "git", "describe"
        } catch(any) {
            // ignore
            return ""
        }
    }

    def commit(message) {
        execute 'git', 'add', '.'
        execute 'git', 'commit', '--allow-empty', '--allow-empty-message', '-m', message
    }

    def push() {
        execute 'git', 'push'
    }
}