(function (root) {
   root.FeedbackApplicationTool = function (authorizationPolicy, collection) {

       var initialize = function(){
           purge();
           renderConfirmDialog();
           $('#kidentifier').text(authorizationPolicy.username);
           bindEvents();
       };

       var options = {
           message: 'Palaute',
           saveButton: 'Lähetä',
           cancelButton: 'Peruuta',
           saveCallback: function(){
               addSpinner();
               collection.sendFeedbackApplication( $(".form-horizontal").serializeArray());
               },
           cancelCallback: function(){  $(':input').val(''); },
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

       var bindEvents = function() {
           $('.confirm-modal .cancel').on('click', function() {
               options.cancelCallback();
           });
           $('.confirm-modal .save').on('click', function() {
               options.saveCallback();
           });
           $(' .confirm-modal .sulje').on('click', function() {
               options.closeCallback();
           });

           eventbus.on("feedback:send", function() {
               removeSpinner();
               new GenericConfirmPopup("Kiitos palautteesta", {type: 'alert'});
           });
           eventbus.on("feedback:failed",function() {
               removeSpinner();
               new GenericConfirmPopup("Palautteen lähetyksessä esiintyi virhe. Yritys toistuu automaattisesti hetken päästä.", {type: 'alert'});
           });
       };


       var confirmDiv =
           '<div class="modal-overlay confirm-modal" id="feedback">' +
                '<div class="modal-dialog">' +
                    '<div class="content">' + options.message + '<a class="header-link sulje"">Sulje</a>' + '</div>' +
                    '<form class="form form-horizontal" role="form"">' +
                        '<label class="control-label" id="title">Anna palautetta OTH-sovelluksesta</label>'+
                        '<div class="form-group">' +
                            '<label class="control-label">Palautteen tyyppi</label>' +
                            '<select name="feedbackType" class="form-control">'+
                                '<option value="" selected disabled hidden>-</option>' +
                                '<option value="Bugi">Bugi</option>'+
                                '<option value="Kehitysehdotus">Kehitysehdotus </option>'+
                                '<option value="Vapaa palaute">Vapaa palaute</option>'+
                            '</select>'+

                            '<label class="control-label">Otsikko</label>' +
                            '<input type="text" name="headline" class="form-control">' +

                            '<label class="control-label">Palaute</label>' +
                            '<textarea name="freeText" id="freetext" class="form-control"></textarea>'+

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
                    '<div class="actions">' +
                       '<button class = "btn btn-primary save">' + options.saveButton + '</button>' +
                       '<button class = "btn btn-secondary cancel">' + options.cancelButton + '</button>' +
                    '</div>' +
                '</div>' +
           '</div>';
   };
})(this);