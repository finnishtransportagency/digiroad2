(function (root) {
    root.FeedbackModel = function() {
        var me = this;
        var backend;

        this.initialize = function (feedbackBackend) {
            backend = feedbackBackend;
        };

        this.send = function (data) {
            var success = function () {
                eventbus.trigger("feedback:send");
            };

            var failure = function () {
                eventbus.trigger("feedback:failed");
            };
            backend.sendFeedback(convertFromToJSON(data), success, failure);
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