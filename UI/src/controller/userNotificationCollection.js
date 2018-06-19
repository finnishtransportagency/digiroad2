(function(root) {
  root.UserNotificationCollection = function(backend) {

    var notifications = [];

    this.fetchUnreadNotification = function() {
      backend.getUserNotificationInfo().then(
        function (result) {
          notifications = result;
            var sortedNotifications = _.sortByOrder(result, function (notification) {
            return notification.createdDate;
          }, 'asc');

          if (_.some(result, function (item) { return item.unRead; }))
            eventbus.trigger('userNotification:fetched', sortedNotifications) ;
        });
    };


    this.fetchAll = function() {
      return _.sortByOrder(notifications, function (notification) {
        return notification.createdDate;
      }, 'asc');
    };

    this.setNotificationToRead = function(id) {
      var objIndex = _.findIndex( notifications, function(notification) {return notification.id.toString() === id;});
        notifications[objIndex].unRead = false;
    };

  };
})(this);
