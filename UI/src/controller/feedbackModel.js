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

        this.get = function(){
            //returnar objecto com info necessaria ao form
            return model.get();
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


// (function(root) {
//     root.FeedbackModel = function() {
//
//         var send = function(backend, data){
//             var success = function(){
//                 eventbus.trigger("feedback:send");
//             };
//
//             var failure = function(){
//                 eventbus.trigger("feedback:failed");
//             };
//             backend.sendFeedback(convertFromToJSON(data), success, failure);
//         };
//
//         var get = function(model){
//             //returnar objecto com info necessaria ao form
//             return model.get();
//         };
//
//         var convertFromToJSON = function(form){
//             var json = {};
//             jQuery.each(form, function(){
//                 json[this.name] = this.value || '';
//             });
//             return JSON.stringify({body : json});
//         };
//
//     };
//
//     return{
//         send: send,
//         get: get
//     };
//
// })(this);

//
//
// (function(feedbackModel) {
//
//     feedbackModel.send = function(backend, data){
//         var success = function(){
//             eventbus.trigger("feedback:send");
//         };
//
//         var failure = function(){
//             eventbus.trigger("feedback:failed");
//         };
//         backend.sendFeedback(convertFromToJSON(data), success, failure);
//     };
//
//
//     feedbackModel.get = function(model){
//         //returnar objecto com info necessaria ao form
//         return model.get();
//     };
//
//     var convertFromToJSON = function(form){
//         var json = {};
//         jQuery.each(form, function(){
//             json[this.name] = this.value || '';
//         });
//         return JSON.stringify({body : json});
//
//     };
//
// })(window.feedbackModel = window.feedbackModel || {});