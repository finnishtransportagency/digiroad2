(function (root) {
    root.FeedbackDataTool = function (feedbackCollection, model, layerName, authorizationPolicy, eventCategory) {
        var me = this;
        me.model = model;
        me.collection = feedbackCollection;

        function events() {
            return _.map(arguments, function(argument) { return eventCategory + ':' + argument; }).join(' ');
        }

        this.renderFeedbackLink = function (enable) {
            var infoContent = $('#information-content');
            if (enable && !infoContent.find('#feedback-data').length)
                infoContent.append('<a id="feedback-data" href="javascript:void(0)" class="feedback-data-link" >Anna palautetta kohteesta</a>');
            else if(!enable) {
                infoContent.find('#feedback-data').remove();
            }

            $('#feedback-data').on('click', function(){
               me.open();
            });
        };

        this.open = function(){
            renderDialog(getData(), layerName);
            bindEvents();
            applicationModel.setApplicationkState(applicationState.Feedback);
        };

        var closeFeedback = function(){
            purge();
            me.renderFeedbackLink(false);
            applicationModel.setApplicationkState(applicationState.Normal);
        };

        var initFeedback = function(){
            me.renderFeedbackLink(true);
        };

        var applicationListeners = function(){
            eventbus.on("feedback:send", function() {
                removeSpinner();
                new GenericConfirmPopup("Kiitos palautteesta", {type: 'alert'});
            });
            eventbus.on("feedback:failed",function() {
                removeSpinner();
                new GenericConfirmPopup("Palautteen lähetyksessä esiintyi virhe. Yritys toistuu automaattisesti hetken päästä.", {type: 'alert'});
            });

            eventbus.on('linkProperties:unselected', closeFeedback);

            eventbus.on('linkProperties:selected linkProperties:cancelled', initFeedback);

            eventbus.on(events('selected', 'cancelled'), initFeedback);

            eventbus.on(events('unselect'), closeFeedback);

            eventbus.on('manoeuvres:selected manoeuvres:cancelled',initFeedback);

            eventbus.on('manoeuvres:unselected', closeFeedback);

            eventbus.on('speedLimit:unselect', closeFeedback);

            eventbus.on('speedLimit:selected speedLimit:cancelled',initFeedback);

            eventbus.on(layerName + ':unselected', closeFeedback);

            eventbus.on(layerName + ':selected ' + layerName + ':cancelled' ,initFeedback);

            eventbus.on('asset:modified', initFeedback);

            eventbus.on('asset:closed',initFeedback);
        };

        applicationListeners();

        var bindEvents = function () {
            $('.feedback-modal .cancel').on('click', function() {
                $('.feedback-modal :input').val('');
            });
            $('.feedback-modal .save').on('click', function() {
                addSpinner();
                applicationModel.setApplicationkState('normal');
                collection.sendFeedbackData( $(".form-horizontal").serializeArray());
            });
            $(' .feedback-modal .sulje').on('click', function() {
                applicationModel.setApplicationkState('normal');
                purge();
            });

        };

        var addSpinner = function () {
            $('.container').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
        };

        var removeSpinner = function(){
            $('.spinner-overlay').remove();
        };

        var purge = function() {
            $('.feedback-modal').remove();
        };

        var renderDialog = function(selectedAsset, layer) {
            var dialog = createFeedbackForm(selectedAsset, layer);
            $('.container').append(dialog);
        };

        var getData = function(){
           return model.get();
        };

        var setDropdownValue = function(layer, dialog){
           if(layer === 'linkProperty')
               dialog.find('#feedbackDataType').val('Geometriapalaute');
           else
               dialog.find('#feedbackDataType').val('Aineistopalaute');
        };

        var getLinkIds = function (selectedAsset) {
           return _.map(selectedAsset, function(selected){ return selected.linkId; }).join(',');
        };

        var getAssetId = function(selectedAsset, layer){
            if(layer === 'linkProperty')
                return '123456';
            else
                return _.map(selectedAsset, function(selected){ return selected.id; }).join(',');
        };

        var userEditableFields = function(){
            return $(
                     '<label class="control-label">Nimi</label>' +
                     '<input type="text" name="name" class="form-control">' +

                     '<label class="control-label">Sähköposti</label>' +
                     '<input type="text" name="email" class="form-control">' +

                     '<label class="control-label">Puhelinnumero</label>' +
                     '<input type="text" name="phoneNumber" id="phoneNumber" class="form-control">');

        };

        var createFeedbackForm = function(selectedAsset, layer) {


           var  dialog =  $('<div class="modal-overlay feedback-modal" id="feedbackData">' +
                        '<div class="modal-dialog">' +
                            '<div class="content">Anna palautetta kohteesta<a class="header-link sulje"">X</a>' + '</div>' +
                            '<form class="form form-horizontal" role="form"">' +
                            '<div class="form-group" id="feedbackForm">' +

                                '<label class="control-label">Linkin id</label>' +
                                '<label id="linkId"></label>'+

                                '<label class="control-label">Kohteen id</label>' +
                                '<label id="assetId"></label>'+

                                '<label class="control-label" id="feedbackType">Palautteen tyyppi</label>' +
                                '<select name="feedbackDataType" id ="feedbackDataType" class="form-control">'+
                                    '<option value="Geometriapalaute">Geometriapalaute </option>'+
                                    '<option value="Aineistopalaute">Aineistopalaute</option>'+
                                '</select>' +

                                '<label class="control-label">Palaute</label>' +
                                '<textarea name="freeText" id="freetext" class="form-control"></textarea>'+

                                '<label class="control-label">K-tunnus</label>' +
                                '<label id="kidentifier"></label>'+
                            '</div>' +
                            '</form>' +
                            '<div class="actions">' +
                                '<button class = "btn btn-primary save">Lähetä</button>' +
                                '<button class = "btn btn-secondary cancel">Peruuta</button>' +
                            '</div>' +
                        '</div>' +
                    '</div>');

           setDropdownValue(layer, dialog);
           dialog.find("#kidentifier").append(authorizationPolicy.username);
           dialog.find("#linkId").append(getLinkIds(selectedAsset));
           dialog.find("#assetId").append(getAssetId(selectedAsset, layer));
           dialog.find("#feedbackForm").append(userEditableFields());
           return dialog;
        };
    };
})(this);