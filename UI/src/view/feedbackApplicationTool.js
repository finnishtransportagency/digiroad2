(function (root) {
   root.FeedbackApplicationTool = function (authorizationPolicy, collection) {

       
       var MAX_CHARACTER_LENGTH = 3747;
       
       var initialize = function(){
           eventbus.trigger('closeFeedBackData');
           purge();
           renderConfirmDialog();
           $('#kidentifier').text(authorizationPolicy.username);
           $(".feedback-message-count").text(`${0}/${MAX_CHARACTER_LENGTH}`);
           bindEvents();
       };

       var reopen = function(){
           renderConfirmDialog();
           $('#kidentifier').text(authorizationPolicy.username);
           bindEvents();
       };

       var options = {
           message: 'Palaute',
           saveButton: 'Lähetä',
           cancelButton: 'Peruuta',
           saveCallback: function () {
               addSpinner();
               var message = $(".feedback-message").serializeArray();
               if (message[0].value.length <= 3747) {
                   collection.sendFeedbackApplication(message);
               } else {
                   eventbus.trigger("feedback:tooBig");
               }
           },
           closeCallback: function() { purge(); }
       };

       $('#startfeedback').on('click', initialize);

       var purge = function() {
           $('.confirm-modal').remove();
       };

       var renderConfirmDialog = function() {
           $('.container').append(confirmDiv);
       };

       var addSpinner = function () {
           $('.container').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
       };

       var removeSpinner = function(){
           $('.spinner-overlay').remove();
       };

       var setSaveButtonState = function(){
           $('.confirm-modal .save').prop('disabled', _.isEmpty($('.confirm-modal #feedbackType').val()));
       };

       var bindEvents = function() {

           $('.confirm-modal .cancel').on('click', function() {
             options.closeCallback();
           });
           $('.confirm-modal .save').on('click', function() {
               options.saveCallback();
           });
           $(' .confirm-modal .sulje').on('click', function() {
               options.closeCallback();
           });

           $('#feedbackType').change(function(){
               setSaveButtonState();
           });

           eventbus.on("feedback:send", function() {
               removeSpinner();
               new GenericConfirmPopup("Kiitos palautteesta", {type: 'alert'});
           });

           eventbus.on("feedback:failed",function() {
               removeSpinner();
               new GenericConfirmPopup("Palautteen lähetyksessä esiintyi virhe. Yritys toistuu automaattisesti hetken päästä.", {type: 'alert'});
           });

           $(".feedback-message").on("change keyup paste",function() {
               console.log("launhed2");
               var message = $(".feedback-message").serializeArray();
               $(".feedback-message-count").text(`${message[0].value.length}/${MAX_CHARACTER_LENGTH}`);
           });
           
           eventbus.on("feedback:tooBig",function() {
               removeSpinner();
               new GenericConfirmPopup("Palaute oli liian pitkä. Maksimi merkki määrä on 3747", {type: 'alert',okCallback:reopen});
           });
       };

       var suggestionText = 'Jättääksesi palautetta aineistosta, valitse haluamasi linkki ja <br /> valitse "Anna palautetta kohteesta" lomakkeen oikeasta yläkulmasta';

       var confirmDiv =
           '<div class="modal-overlay confirm-modal" id="feedback">' +
                '<div class="modal-dialog">' +
                    '<div class="content">' + options.message + '<a class="header-link sulje">Sulje</a>' + '</div>' +
                    '<form class="form form-horizontal" role="form">' +
                        '<label class="control-label" id="title">Anna palautetta ylläpitosovelluksesta alapuolelle</label>'+
                        '<label class="control-label" id="suggestion-label">' + suggestionText + '</label>' +
                        '<div class="form-group">' +
                            '<label class="control-label">Palautteen tyyppi</label>' +
                            '<select name="feedbackType"  id="feedbackType" class="form-control">'+
                                '<option value="" selected disabled hidden>-</option>' +
                                '<option value="Bugi">Bugi</option>'+
                                '<option value="Kehitysehdotus">Kehitysehdotus </option>'+
                                '<option value="Vapaa palaute">Vapaa palaute</option>'+
                            '</select>'+

                            '<label class="control-label">Otsikko</label>' +
                            '<input type="text" name="headline" class="form-control">' +

                            '<label class="control-label">Palaute</label>' +
                            '<textarea maxlength="3747" name="freeText" id="freetext" class="form-control feedback-message"></textarea>'+
                            '<label class="control-label">Merkkien määrä:</label>' +
                            '<label id="feedback-message-count" class="feedback-message-count"></label>' +
           
                            '<label class="control-label">K-tunnus</label>' +
                            '<label id="kidentifier"></label>'+

                            '<label class="control-label">Nimi</label>' +
                            '<input type="text" name="name" class="form-control">' +

                            '<label class="control-label">Sähköposti</label>' +
                            '<input type="text" name="email" class="form-control">' +

                            '<label class="control-label">Puhelinnumero</label>' +
                            '<input type="text" name="phoneNumber" id="phoneNumber" class="form-control">' +

                        '</div>' +
                    '</form>' +
                    '<div class="actions feedback-actions">' +
                            '<button class = "btn btn-primary save" disabled>' + options.saveButton + '</button>' +
                            '<button class = "btn btn-secondary cancel">' + options.cancelButton + '</button>' +
                    '</div>' +
                '</div>' +
           '</div>';
   };
})(this);

