from flask import Flask, render_template, request, redirect
import requests
from datetime import datetime
import json

app = Flask(__name__)

post_user_dict = dict()


# noinspection PyUnresolvedReferences
@app.route("/")
def main_page() -> "html":
    return render_template("main.html")


# noinspection PyUnresolvedReferences
@app.route("/register_subscription", methods=["POST"])
def register_subscription() -> "html":
    subscriber_id = request.form["subscriber_id"]
    user_name = request.form["user_name"]
    update_interval = request.form["update_interval"]
    response = requests.post('http://localhost:8080/post-subscription/twitter/',
                             json={"twitterUserName": user_name, "subscriberId": subscriber_id,
                                   "updateInterval": update_interval})
    error = ""
    subscription_id = ""
    now = datetime.now().strftime("%d/%m/%Y %H:%M:%S")
    if response.status_code == requests.codes.ok:
        subscription_id = response.json()["subscriptionId"]
    else:
        error = response.text
    return render_template("main.html", subscriber_id=subscriber_id, user_name=user_name,
                           subscription_id=subscription_id, error=error, now=now)


# noinspection PyUnresolvedReferences
@app.route("/get_last_tweets", methods=["POST"])
def get_last_tweets() -> "html":
    subscription_id = request.form["subscription_id"]
    user_name = request.form["user_name"]
    subscriber_id = request.form["subscriber_id"]
    response = requests.post('http://localhost:8080/post-subscription/twitter/demo-result/queue/' + subscription_id)
    if subscription_id not in post_user_dict:
        post_user_dict[subscription_id] = []
    error = ""
    now = datetime.now().strftime("%d/%m/%Y %H:%M:%S")
    if response.status_code == requests.codes.ok:
        post_user_dict[subscription_id] += json.loads(response.text)
    else:
        error = response.text
    return render_template("main.html", subscription_id=subscription_id, tweets=post_user_dict[subscription_id],
                           error=error, user_name=user_name, subscriber_id=subscriber_id, now=now)


# noinspection PyUnresolvedReferences
@app.route("/delete_subscription", methods=["POST"])
def delete_subscription() -> "html":
    user_name = request.form["user_name"]
    subscriber_id = request.form["subscriber_id"]
    requests.delete('http://localhost:8080/post-subscription/twitter/' + subscriber_id + '/' + user_name)
    return redirect("/")


if __name__ == '__main__':
    app.run(debug=True, threaded=True)
