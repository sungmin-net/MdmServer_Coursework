# java-mdmServer-courseWork

기본적으로, Eclipse 에서 Java 로 구현한 1:N TLS 서버입니다. Java 빌드 환경 설정에 익숙하신 분들은 Windows + Eclipse 말고 편하신대로 사용하셔도 좋습니다.


### 1. Precondition

##### 1.1. 이클립스 설치

21.09.23일 기준으로 아래 링크가 최신입니다. 다른 버전을 사용해도 무방합니다.

https://www.eclipse.org/downloads/download.php?file=/oomph/epp/2021-09/R/eclipse-inst-jre-win64.exe&mirror_id=105

##### 1.2. OpenJDK 다운로드

21.09.23일 기준으로 아래 링크가 최신 stable 버전입니다. ~~비생산적인 삽질~~ 버전 충돌을 피하기 위해 아래 버전 사용을 권장합니다.

https://download.java.net/java/GA/jdk16.0.2/d4a915d82b4c4fbb9bde534da945d746/7/GPL/openjdk-16.0.2_windows-x64_bin.zip

##### 1.3. JAVA_HOME 등 환경 변수 설정

https://vmpo.tistory.com/6 등 다양한 블로그를 참고합니다.



### 2. Download

git이 설치되어 있어야 합니다. (git 설치는 인터넷을 참고하세요.)

git clone로 파일을 다운로드할 곳을 소스경로로 지정하고, Git Bash에 아래의 명령어를 입력합니다.

소스경로> git clone https://github.com/sungmin-net/java-mdmServer-courseWork.git



### 3. Import

설치된 Eclipse를 열어서 상단 메뉴에 File -> Import -> Existing Projects into Work space -> Select root directory -> git clone 한 디렉토리 선택 -> Finish

##### 3.1. 이클립스 프로젝트에 빨간 엑스가 생기면?

아마 JRE 경로가 상이해서 일겁니다. 프로젝트 우클릭 -> Properties -> Java Build Path -> 빨강 엑스 있는 모듈을 자신의 경로로 수정(예, JRE System library 선택 -> Edit -> Workspace default JRE -> Finish -> Apply and Close



### 4. Run

이클립스의 기본 실행 단축키는 Ctrl + F11 일겁니다. 서버 코드(MdmServerMain.java/ src > (default package) 안에 있습니다.)를 실행했을 때 다음의 텍스트가 콘솔에 뜨면 정상입니다.

[21.08.09 21:31:24] Wait for the client request

관리자 콘솔은 UI가 ~~아직?~~ 없습니다. MdmAdminMain 을 Ctrl + F11 로 실행하면 관련된 내용이 ServerPolicies.json 에 기록됩니다.

##### 4.1. Error: LinkageError occurred while loading main class MdmServerMain

상기 에러가 나면 clean build 가 필요합니다. 다음과 같이 대응합니다.

Eclipse -> Project -> Clean -> 'Clean all projects' 체크 해제하고, UranPolicyServer 체크 -> 'Start a build immediately' 체크하고, Build only the selected protjects 선택 -> Clean

아직 원인은 모르겠지만, 상기 방법이 잘 안먹히는 경우가 있습니다. 프로젝트의 bin 폴더에 class 파일이 생성되지 않을 때는, 임의의 Java 프로젝트를 만들고, java 파일들을 src 폴더로 복사하고, 3개의 부속 파일(json-20210307.jar, ServerPolicies.json, UranMdmServer.p12) 를 프로젝트 폴더에 복사하여, 각자의 환경에서 새 프로젝트로 만드는 방법이 가장 쉬운 방법 같습니다. (더 좋은 방법 있으면 이 파일에 공유바랍니다.)



### 5. Upload

Eclipse -> File -> Export -> General -> File System -> 작업한 프로젝트 선택 -> Finish 로 임의 경로로 떨군 후에, git clone 한 디렉토리에서 (중요) git pull 후, 수정한 파일을 복사하고 커밋을 작성하고 push 하는 것을 권장합니다. 



### 6. Appendix

##### 6.1. KeyStore

UranMdmServer.p12 파일은 다음의 keytool 명령어로 생성하였습니다.

keytool -genkeypair -rfc -keyalg rsa -keysize 2048 -keystore UranMdmServer.p12 -storetype pkcs12 -storepass mmlabmmlab -validity 3650 -alias UranMdmServer -dname CN=UranMdmServer

##### 6.2. Protocol

Client→Server: Magic + RsaEnc(Version + Cmd + UserId) + ServAlias

Client←Server: Magic + ToBeSigned(Version + TimeStamp + CurPolicies) + ServSign
