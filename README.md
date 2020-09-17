# Post streamer [draft]

### Intro
Basic application for real-time streaming of posts from social medias. Is based on
pluggable services which execute API calls to respective social media endpoints.
Currently, **only service that is implemented is the service for retrieving tweets from Twitter.**
First version of the service fetches last tweets for a given user, 
streaming of tweets based on additional filters (tag, content etc.) could be considered.

Streaming of posts is subscription based. It means that user should be uniquely identified when sending a
subscription request. Authentication of the user is not implemented and mocked with a simple string `subscriber_id` that should 
be sent alongside the subscribtion request. One user of the application can request only one subscription to a given
 social media account, at the same time there is no limit of number of social media accounts
 one user of the application can request. 

Application fetches only posts published after subscription registration. There is no guarantee
that all the published posts would be delivered to the subscriber as component that polls is **stateless**
and after each restart it fetches the last post and starts streaming from this post. Next step in development should be
refactoring of the mentioned component to add state and ensure seamless streaming of all posts published 
after the subscription requested. 

When the application starts to fetch posts new "topic" is created in a messaging system. All the new posts
are to be sent to the topic and can be retrieved by the user (web-app, mobile-app etc.). When user sends a subscription requests
to the application endpoint it returns necessary data needed for retrieving messages from the topic.

Messaging service is simulated with an in-memory map that holds messages for each user. Request to a specified endpoint 
results in return of all last posts (messages). Fetched messages are removed from the map. 

### Solution
For streaming of tweets usage of a dedicated Twitter API endpoint was originally considered. Twitter 
streaming endpoint requires stable HTTP connection and could be "unavailable" for a given period of time,
when a client closed/lost connection and tries to reconnect (Twitter introduces it to save own resources). There are also
additional limitations, for example on number of connections (limited to 1 for a basic developer account). 

Alternative solution with REST polling was prioritised taking the above-mentioned facts into consideration. Solution is 
based on executing of two requests to the dedicated Twitter API endpoint:
1. First request fetches identification of the last published post (if available)
2. Periodical requests to the same endpoint for fetching posts published after the last published post. 

#### REST API
Application exposes following **REST endpoints:**
#### /post-subscription/twitter (POST)
Creation of a subscription. Request should contain:
* subscriber id, 
* twitter account to subscribe to
* polling interval (optional). 

Requester can expect: 
* 200: confirmation with "topic" id if the subscription is created 
* 409: if subscription already exists

#### /post-subscription/twitter/{subscriberId} (DELETE)
Cancellation of all subscriptions for a subscriber.
Requester can expect: 
* 202: confirmation that cancellation request is accepted

#### /post-subscription/twitter/{subscriberId}/{twitterUserName} (DELETE)
Cancellation of a given subscription for a subscriber.
Requester can expect: 
* 202: confirmation that cancellation request is accepted

#### /twitter/demo-result/queue/{queueId} (POST)
Simulates messaging service: returns all "messages" (tweets) from the "topic"
Requester can expect: 
* 200: json object with array that contains all not-retrieved messages

#### FETCHING
Fetching of posts is executed by dedicated components (actors). [Each subscription actor] serves as a poller for a given subscription. 
When a subscription request is registered a new actor is created. This actor starts to poll a respective endpoint (through a 
pluggable fetching service) and creates a [child actor] that is in charge of publishing new posts to a dedicated "topic".

As mentioned above subscription actors are stateless. Posts published under actor restart (when for example exception is thrown)
would be lost because the actor will fetch the last published post as starting point for streaming (info about last post before restart
is lost). This can be improved by adding state to actors (via persisting info about last published post) 

### Technologies
#### Spring
Backend server is build using Spring framework. It is supported by most of the infrastructures and makes solution
extendable and flexible.

#### Akka
Akka as toolkit and runtime is suitable for developing of concurrent and distributed applications on the JVM. It supports
cluster approach and integration with various third-party systems. 

### Further development
1. Currently, application is not covered by tests (first priority was to create a functional presentation of basic concepts).
The first step in further development is test coverage.
2. Developments of integration with pluggable messaging services for connection to messaging systems.
3. Refactoring of actors to add state and ensure seamless streaming of posts even in the case of actor restarts.

### How to run
1. Connection to Twitter API requires Authentication Token (Bearer Token) that should be available via `TWITTER_BEARER_TOKEN`
environment variable (can be received through Twitter developer portal)
2. Backend server can be run:
    - directly from IDE (IntelliJ) 
    - using Docker: `docker build -t poststreamerdemosc .`, `docker run -p 8080:8080 --env-file ./env.list poststreamerdemosc` (it's necessary to add a file `env.list` that contains `TWITTER_BEARER_TOKEN=<TOKEN>`)
    - from command line with java: `export TWITTER_BEARER_TOKEN=<TOKEN>`, `./mvnw package && java -jar target/poststreamerdemosc-0.0.1-SNAPSHOT.jar`
3. demo web-app can be run: `cd demo_web_app`, `python3 demo_web_app.py`

### demo_web_app
Is just for demonstration of the application functionality and based on more than naive approach.
To see the functionality:
1. start the server
2. start web-app
3. open [http://127.0.0.1:5000/](http://127.0.0.1:5000/) in browser
4. register a subscription (choose a random subscriber id, existing Twitter user, your self for efficiency)
5. publish new tweet(s)
6. check results in the web-app


[Each subscription actor]: src/main/kotlin/me/contrapost/poststreamerdemosc/actors/PostSubscriptionActor.kt
[child actor]: src/main/kotlin/me/contrapost/poststreamerdemosc/actors/PostPublisherDemoActor.kt
