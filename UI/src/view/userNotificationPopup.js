window.UserNotificationPopup = function(models) {

  var me = this;

  this.initialize = function() {
    eventbus.on('userNotification:fetched', function(result) {
        renderDialog(result);
    });

    models.fetchUnreadNotification();
  };

  var options = {
    message: 'Tiedotteet',
    saveButton: '',
    cancelButton: 'Sulje',
    saveCallback: function(){},
    cancelCallback: function(){},
    closeCallback: function() { purge(); }
  };

  var leftList = function(notifications, selectedItem) {
    return  _.map(notifications, function(item) {
      var classInfo = _.isEqual(selectedItem, item.id) ? 'selected' : item.unRead ? 'bold' : '';

      return '' +
      '<li id=' + item.id + ' class="' + classInfo +'">' +
        '<div>' + item.createdDate + '</div>' +
        '<div>' + item.heading + '</div>' +
      '</li>';
      }).join(' ');
  };

  $('#userNotification').on('click', function() {
      renderDialog(models.fetchAll());
  });

  var contentText = function(notifications, selectedItem) {

    var contentInfo = _.find(notifications, function(notification) {
      return notification.id.toString() === selectedItem.toString();
    });

      return '' +
        '<h3>' +
          '<div>' + contentInfo.heading  + '</div>' +
          '<div>' + contentInfo.createdDate + '</div>' +
        '</h3>' +
        '<div>' + contentInfo.content + '</div>';
  };

  this.createNotificationForm = function(notifications) {
    var someSelectedItem = _.find(notifications, function(notification) {
      return notification.unRead;
    });

    var defaultSelectedItem = someSelectedItem ? someSelectedItem.id : _.first(notifications).id;
    models.setNotificationToRead(defaultSelectedItem.toString());

    return '' +
      '<section>' +
        '<nav>' +
          '<ul class="leftListIds">' +
            leftList(notifications, defaultSelectedItem) +
          '<ul>' +
        '</nav>' +
        '<article>' +
            contentText(notifications, defaultSelectedItem) +
        '</article>' +
      '</section>';
  };

   var renderDialog = function(notifications) {
     $('#work-list').append(me.createNotificationPopUp(notifications)).show();

     $('.confirm-modal .cancel').on('click', function() {
       options.closeCallback();
     });

     $('.leftListIds li').click(function() {
        var allNotification = models.fetchAll();
        models.setNotificationToRead(this.id);
        $(".leftListIds>li").removeClass("selected").removeClass("bold");
        $('article').html(contentText(allNotification, this.id));

       $('#' + this.id.toString()).addClass('selected');
       _.forEach(allNotification, function(item) {
         if (item.unRead)
           $('#' + item.id.toString()).addClass('bold');
       });

     });

  };

   this.createNotificationPopUp  = function(notifications) {
     return '' +
     '<div class="modal-overlay confirm-modal" id="notification">' +
     '<div class="modal-dialog notification">' +
     '<div class="content">' + options.message + '</div>' +
       me.createNotificationForm(notifications) +
     '<div class="actions">' +
     '<button class = "btn btn-secondary cancel">' + options.cancelButton + '</button>' +
     '</div>' +
     '</div>' +
     '</div>';
   };

    var purge = function() {
       $('.confirm-modal').remove();
    };
};