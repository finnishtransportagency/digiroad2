(function(root) {
    root.FeedbackModel = function(backend) {

        this.send = function (data) {
            var success = function(){
                eventbus.trigger("feedback:send");
            };

            var failure = function(){
                eventbus.trigger("feedback:failed");
            };
            backend.sendFeedback(convertFromToJSON(data), success, failure);
        };

        var convertFromToJSON = function(form){
            var json = {};
            jQuery.each(form, function(){
                json[this.name] = this.value || '';
            });
            return JSON.stringify({body : json});
        };

    };

})(this);