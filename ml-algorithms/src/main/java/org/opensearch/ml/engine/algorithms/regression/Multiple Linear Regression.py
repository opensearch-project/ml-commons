# Importing all python library files that are necessary
import pandas as pd # data stuff
import numpy as np # linear algebra expression makeable stuff
import matplotlib.pyplot as plt # graph stuff
from sklearn.metrics import r2_score # accuracy equation stuff

# Kaggle data set for multiple linear regression (3 X 14)
# Dataset values that will be used stored into local variables
data = pd.read_csv("sample_data/mult.csv")
x = data.drop("deneyim", axis=1) # data frames of x_train
y = data["deneyim"]/15 # data frames of y_train
m = len(y) # how many rows
cols = len(x.columns) # how many features

# Local variables for the program
epochs = 450 # no. of iterations
alpha = 1.0e-1 # Learning rate
numx = (x.values)
numy = (y.values)
f_wb = np.zeros(m) #Predicted Values
cost = np.zeros(epochs)
iter = np.arange(epochs)
dw = np.zeros(cols)
db = 0
w = np.zeros(cols)
b = 10/15 #scaling w/ max value from y

#Scaling features before modeling
xmax = np.max(numx, axis=0)
numx = numx / xmax

# Gradient Descent with Multiple Linear Regression
for i in range(epochs):
  dw = np.zeros(cols) # Reset values after each iteration
  db = 0
  costSum = 0
  for row in range(m): # Go down each row vector
    f_wb[row] = (np.dot(w,numx[row])) + b # Linear Regression model equation
    costSum += ((f_wb[row] - numy[row])** 2)/(2*m) # Getting the cost of teh model
    for col in range(cols): # Getting the derivative for Gradient Descent
      dw[col] += ((f_wb[row] - numy[row]) * numx[row, col]) / m # Looping through each feature to get w1 -> wN
    db += (f_wb[row]-numy[row])/m # just b

  w -= alpha * dw # Rinse and repeat for the weightSSS and b
  b -= alpha * db
  cost[i] = costSum # Cost of final prediction for the weights and biases

# Graphing Cost V Iteration to see how many epochs is necessary
plt.plot(iter,cost)
print("Cost in depth: " + str(cost[epochs-1])) # cost
print("The accuracy of the model: " + str(r2_score(numy, f_wb))) # Accuracy 97.7%!!!! not too bad :')
