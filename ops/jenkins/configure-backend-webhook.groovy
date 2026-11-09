// One-time init.groovy.d script. Runs inside Jenkins; never prints credentials.
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import jenkins.model.Jenkins
import jenkins.model.GlobalConfiguration
import hudson.security.ACL
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.GitSCM
import hudson.plugins.git.UserRemoteConfig
import com.coravy.hudson.plugins.github.GithubProjectProperty
import com.cloudbees.jenkins.GitHubPushTrigger
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import org.jenkinsci.plugins.github.config.GitHubPluginConfig
import org.jenkinsci.plugins.plaincredentials.StringCredentials
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition

def jenkins = Jenkins.get()
def status = new File(jenkins.rootDir, 'snapvocab-backend-webhook-status.json')
def completed = new File(jenkins.rootDir, 'snapvocab-backend-webhook.complete')
if (completed.exists()) return

try {
    def credential = CredentialsProvider.lookupCredentialsInItemGroup(StandardUsernamePasswordCredentials,
        jenkins, ACL.SYSTEM2, []).find { it.id == 'github-snapvocab' }
    def secret = CredentialsProvider.lookupCredentialsInItemGroup(StringCredentials,
        jenkins, ACL.SYSTEM2, []).find { it.id == 'snapvocab-admin-webhook' }
    assert credential != null && secret != null : 'Required Jenkins credentials are missing'
    assert GlobalConfiguration.all().get(GitHubPluginConfig).hookSecretConfigs.any {
        it.credentialsId == secret.id
    } : 'Existing webhook verification credential is not configured'

    def request = { String method, String path, Object body = null ->
        def builder = HttpRequest.newBuilder(URI.create('https://api.github.com' + path))
            .timeout(Duration.ofSeconds(30))
            .header('Authorization', 'Bearer ' + credential.password.plainText)
            .header('Accept', 'application/vnd.github+json')
            .header('X-GitHub-Api-Version', '2022-11-28')
            .header('User-Agent', 'SnapVocab-Jenkins-Webhook-Setup')
        if (body != null) {
            builder.header('Content-Type', 'application/json')
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(JsonOutput.toJson(body)))
        def response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
            .send(builder.build(), HttpResponse.BodyHandlers.ofString())
        int code = response.statusCode()
        if (code < 200 || code >= 300) {
            throw new IllegalStateException('GitHub ' + method + ' ' + path + ': HTTP ' + code)
        }
        def text = response.body()
        text ? new JsonSlurper().parseText(text) : null
    }

    String repository = '/repos/SnapVocab/snap-vocab-backend'
    String webhookUrl = 'https://jenkins.thanhthbm.online/github-webhook/'
    def hooks = request('GET', repository + '/hooks?per_page=100')
    def hook = hooks.find { it.config?.url == webhookUrl }
    def payload = [name: 'web', active: true, events: ['push'],
        config: [url: webhookUrl, content_type: 'json', insecure_ssl: '0', secret: secret.secret.plainText]]
    hook = hook ? request('PATCH', repository + '/hooks/' + hook.id, payload)
                : request('POST', repository + '/hooks', payload)

    def job = jenkins.getItemByFullName('snapvocab-backend')
    assert job != null && !job.isBuilding() : 'Backend job missing or currently building'
    def scm = new GitSCM(
        [new UserRemoteConfig('https://github.com/SnapVocab/snap-vocab-backend.git', null, null, 'github-snapvocab')],
        [new BranchSpec('*/prod')], false, [], null, null, [])
    def definition = new CpsScmFlowDefinition(scm, 'Jenkinsfile')
    definition.setLightweight(true)
    job.setDefinition(definition)
    job.addProperty(new GithubProjectProperty('https://github.com/SnapVocab/snap-vocab-backend/'))
    job.setTriggers([new GitHubPushTrigger()])
    job.save()
    status.text = JsonOutput.toJson([success: true, hookId: hook.id,
        repository: 'SnapVocab/snap-vocab-backend', branch: 'prod', scheduledPolling: false])
    completed.text = 'Applied successfully\n'
    println('SnapVocab backend webhook configured; prod SCM pipeline enabled; polling disabled.')
} catch (Exception failure) {
    // Do not log HTTP response bodies or objects containing credentials.
    status.text = JsonOutput.toJson([success: false, error: failure.class.simpleName,
        message: failure.message?.replaceAll('(?i)(token|secret|password)=[^ ]+', '$1=[redacted]')])
    println('SnapVocab backend webhook configuration failed; inspect the protected status file.')
}
