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

import org.ajoberstar.gradle.git.release.base.ReleaseVersion
import org.ajoberstar.gradle.git.release.base.TagStrategy
import org.ajoberstar.grgit.Commit
import org.ajoberstar.grgit.Grgit
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static org.ajoberstar.gradle.git.release.semver.ChangeScope.*

/**
 * Created by tobias on 7/26/15.
 */
class SemanticReleaseChangeLogServiceSpec extends Specification {

    Grgit grgit = Mock()
    TagStrategy tagStrategy = new TagStrategy()

    GitRepo repo = new GithubRepo(grgit)

    @Subject
    SemanticReleaseChangeLogService changeLogService = new SemanticReleaseChangeLogService(grgit, repo, tagStrategy)

    def "does not throw an exception if no ticket is referenced"() {
        given:
        Commit commit = new Commit(fullMessage: commitMessage)

        expect:
        changeLogService.closes(commit) == [] as SortedSet

        where:
        commitMessage | _
        ''            | _
        'foo'         | _
        '\n'          | _
    }

    def "finds referenced tickets one on each line"() {
        given:
        Commit commit = new Commit(fullMessage: '''\
            Closes #123
            Fixes #456
        '''.stripIndent())

        expect:
        changeLogService.closes(commit) == ['123', '456'] as SortedSet
    }

    def "finds referenced tickets all on one line"() {
        given:
        Commit commit = new Commit(fullMessage: '''\
            Closes #123, #456
        '''.stripIndent())

        expect:
        changeLogService.closes(commit) == ['123', '456'] as SortedSet
    }

    def "finds referenced tickets on last line"() {
        given:
        Commit commit = new Commit(fullMessage: '''\
            Closes #123'''.stripIndent())

        expect:
        changeLogService.closes(commit) == ['123'] as SortedSet
    }

    def "finds breaking change on same line"() {
        given:
        Commit commit = new Commit(fullMessage: '''\
            Subject

            BREAKING CHANGE: foo bar baz
        '''.stripIndent())

        expect:
        changeLogService.breaks(commit) == 'foo bar baz'
    }

    def "finds breaking change on next lines"() {
        given:
        Commit commit = new Commit(fullMessage: '''\
            Subject

            BREAKING CHANGE:

            foo bar baz
        '''.stripIndent())

        expect:
        changeLogService.breaks(commit) == 'foo bar baz'
    }

    def "finds type from shortMessage"() {
        given:
        Commit commit = new Commit(shortMessage: shortMessage)

        expect:
        changeLogService.type(commit) == type

        where:
        shortMessage             | type
        "did this and that"      | null
        "feat(core): blah blupp" | "feat"
        "feat: blah blupp"       | "feat"
    }

    def "finds component from shortMessage"() {
        given:
        Commit commit = new Commit(shortMessage: shortMessage)

        expect:
        changeLogService.component(commit) == component

        where:
        shortMessage             | component
        "did this and that"      | null
        "feat(core): blah blupp" | "core"
        "feat: blah blupp"       | null
    }

    def "subject does not contain type and component"() {
        given:
        Commit commit = new Commit(shortMessage: shortMessage)

        expect:
        changeLogService.subject(commit) == subject

        where:
        shortMessage             | subject
        "did this and that"      | "did this and that"
        "feat(core): blah blupp" | "blah blupp"
        "feat: blah blupp"       | "blah blupp"
    }

    @Unroll
    def "infers correct ChangeScope #changeScope for commits #commits"() {
        expect:
        changeLogService.changeScope(commits.collect(asCommit)) == changeScope

        where:
        changeScope | commits
        PATCH       | ['perf: foo', 'foo bar']
        PATCH       | ['fix: foo', 'foo bar']
        MINOR       | ['fix: foo', 'feat: baz', 'foo bar']
        MAJOR       | ['fix: foo', 'feat: baz\n\nBREAKING CHANGE: This and that', 'foo bar']
        null        | ['foo bar', 'baz']
    }

    def "changeLog is generated"() {
        given:
        grgit = Grgit.open()
        repo = new GithubRepo(grgit)
        changeLogService = new SemanticReleaseChangeLogService(grgit, repo, tagStrategy)
        String mnemo = repo.mnemo

        when:
        def commits = [
                'fix(component1): foo\n\nCloses #123, #124',
                'fix(component1): bar',
                'fix(component2): baz\n\nCloses #123\nCloses #124',
                'fix: no component\n\nCloses #456, #789',
                'feat: baz\n\nCloses #159\n\nBREAKING CHANGE: This and that', 'foo bar']
        def expected = """\
            <a name="2.0.0"></a>
            # [2.0.0](https://github.com/$mnemo/compare/v1.0.0...v2.0.0) (${
            new java.sql.Date(System.currentTimeMillis())
        })

            ### Bug Fixes

            * no component ([1234567](https://github.com/$mnemo/commit/1234567), closes #456, #789)
            * **component1:**
                * foo ([1234567](https://github.com/$mnemo/commit/1234567), closes #123, #124)
                * bar ([1234567](https://github.com/$mnemo/commit/1234567))
            * **component2:** baz ([1234567](https://github.com/$mnemo/commit/1234567), closes #123, #124)

            ### Features

            * baz ([1234567](https://github.com/$mnemo/commit/1234567), closes #159)

            ### BREAKING CHANGES

            * This and that
        """.stripIndent()

        then:
        changeLogService.changeLog(commits.collect(asCommit), new ReleaseVersion(previousVersion: '1.0.0', version: '2.0.0', createTag: true)).toString() == expected
    }

    static asCommit = { new Commit(fullMessage: it, shortMessage: it.readLines().first(), id: '123456789abc') }

}
