# Shamir’s Secret Sharing Solver

This project implements a solver for **Shamir’s Secret Sharing** scheme.  
It reconstructs the original secret using provided share values in different bases.  

## 📂 Repository Contents
- **ShamirSolver.java** → Main program source code  
- **ShamirSolver.class** → Compiled bytecode  
- **ShamirSolver$Frac.class** → Fraction helper class  
- **ShamirSolver$InputData.class** → JSON input data handler  
- **ShamirSolver$Share.class** → Share structure  
- **input1.json** → Test Case 1 input file  
- **input2.json** → Test Case 2 input file  

## ⚙️ How It Works
- Reads input JSON files describing:
  - `n` (total shares)
  - `k` (minimum shares required)
  - Shares: base + encoded value  
- Converts values from their respective bases.  
- Applies **Lagrange interpolation** to reconstruct the secret.  
- Outputs the recovered secret as a decimal integer.  

## ▶️ How to Compile & Run

### 1. Compile
javac ShamirSolver.java

### 2. Run with Input
java ShamirSolver < input1.json 
java ShamirSolver < input2.json 

### 3. Example Output
Output - 1:
{
  "secret": "3",
  "inliers": [1, 2, 3, 6],
  "outliers": []
}

Output - 2:
{
  "secret": "79836264049851",
  "inliers": [1, 3, 4, 5, 6, 7, 9, 10],
  "outliers": [2, 8]
}

