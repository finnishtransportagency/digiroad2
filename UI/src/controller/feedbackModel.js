(function (root) {
    root.FeedbackModel = function(feedbackBackend) {
        var me = this;
        var backend = feedbackBackend;


        this.sendFeedbackApplication = function (data) {
            var success = function () {
                eventbus.trigger("feedback:send");
            };

            var failure = function () {
                eventbus.trigger("feedback:failed");
            };
            backend.sendFeedbackApplication(convertFromToJSON(data), success, failure);
        };


        this.sendFeedbackData = function (data) {
            var success = function () {
                eventbus.trigger("feedback:send");
            };

            var failure = function () {
                eventbus.trigger("feedback:failed");
            };
            backend.sendFeedbackData(convertFromToJSON(data), success, failure);
        };


        this.get = function (model) {
            //returnar objecto com info necessaria ao form
            return model.get();
        };

        var convertFromToJSON = function (form) {
            var json = {};
            jQuery.each(form, function () {
                json[this.name] = this.value || '';
            });
            return JSON.stringify({body: json});

        };
    };
})(this);