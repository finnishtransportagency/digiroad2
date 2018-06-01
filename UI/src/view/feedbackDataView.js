(function(feedbackDataView) {
    feedbackDataView.initialize = function (model) {

        var feedbackModel =  new FeedbackModel(null, model);
        var feedback = feedbackModel.get();



        //
        // bindEvents();
        //
        // var bindEvents = function () {
        //
        // };
        //
        // var createFeedbackForm = function () {
        //
        // };
    }
})(window.FeedbackDataView = window.FeedbackDataView || {});
